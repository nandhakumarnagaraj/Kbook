package com.khanabook.saas.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetStorageService {

	private final RestaurantProfileRepository restaurantProfileRepository;

	@Value("${kbook.cdn.base-path}")
	private String basePath;

	@Value("${kbook.cdn.url-prefix}")
	private String urlPrefix;

	@Value("${kbook.cdn.tmp-path}")
	private String tmpPath;

	@Value("${kbook.cdn.cwebp-bin}")
	private String cwebpBin;

	@Value("${kbook.cdn.max-upload-bytes}")
	private long maxUploadBytes;

	@Transactional
	public AssetUploadResult uploadLogo(Long restaurantId, MultipartFile file) {
		// Logos: lossy WebP at q=85; small files, photo-like content tolerates it.
		return uploadAsset(restaurantId, file, "logo", false, 85);
	}

	@Transactional
	public AssetUploadResult uploadUpiQr(Long restaurantId, MultipartFile file) {
		// UPI QR: lossless WebP; any pixel artifact can break QR scanning.
		return uploadAsset(restaurantId, file, "upi_qr", true, 100);
	}

	@Transactional
	public void deleteLogo(Long restaurantId) {
		deleteAsset(restaurantId, "logo");
	}

	@Transactional
	public void deleteUpiQr(Long restaurantId) {
		deleteAsset(restaurantId, "upi_qr");
	}

	private AssetUploadResult uploadAsset(Long restaurantId, MultipartFile file, String kind,
			boolean lossless, int quality) {
		validate(file);

		Path tmp = null;
		try {
			tmp = saveToTmp(file);

			RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
					.orElseThrow(() -> new IllegalStateException("Restaurant profile not found: " + restaurantId));

			int newVersion = nextVersion(profile, kind);
			String filename = kind + "_v" + newVersion + ".webp";
			Path target = Paths.get(basePath, String.valueOf(restaurantId), filename);
			Files.createDirectories(target.getParent());

			runCwebp(tmp, target, lossless, quality);

			if (!Files.exists(target) || Files.size(target) == 0) {
				throw new IllegalStateException("WebP conversion produced no output");
			}

			deleteOldVersions(restaurantId, kind, newVersion);

			String url = normalizedUrlPrefix() + restaurantId + "/" + filename;
			applyToProfile(profile, kind, url, newVersion);
			restaurantProfileRepository.save(profile);

			log.info("Uploaded {} for restaurant {} -> {}", kind, restaurantId, url);
			return new AssetUploadResult(url, newVersion);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Asset upload failed", e);
		} catch (IOException e) {
			throw new RuntimeException("Asset upload failed", e);
		} finally {
			if (tmp != null) {
				try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
			}
		}
	}

	private void deleteAsset(Long restaurantId, String kind) {
		RestaurantProfile profile = restaurantProfileRepository.findByRestaurantId(restaurantId)
				.orElseThrow(() -> new IllegalStateException("Restaurant profile not found: " + restaurantId));

		Path dir = Paths.get(basePath, String.valueOf(restaurantId));
		if (Files.exists(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(p -> p.getFileName().toString().startsWith(kind + "_v"))
						.forEach(p -> {
							try { Files.deleteIfExists(p); } catch (IOException ignored) {}
						});
			} catch (IOException e) {
				log.warn("Failed to list asset dir for restaurant {}", restaurantId, e);
			}
		}

		applyToProfile(profile, kind, null, 0);
		restaurantProfileRepository.save(profile);
	}

	private void validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}
		if (file.getSize() > maxUploadBytes) {
			throw new IllegalArgumentException("File too large; max " + maxUploadBytes + " bytes");
		}
		String contentType = file.getContentType();
		if (contentType == null || !(contentType.startsWith("image/"))) {
			throw new IllegalArgumentException("Only image uploads are allowed");
		}
	}

	private Path saveToTmp(MultipartFile file) throws IOException {
		Files.createDirectories(Paths.get(tmpPath));
		String suffix = guessExtension(file.getContentType());
		Path tmp = Paths.get(tmpPath, UUID.randomUUID() + suffix);
		try (var in = file.getInputStream()) {
			Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
		}
		return tmp;
	}

	private String guessExtension(String contentType) {
		if (contentType == null) return ".bin";
		return switch (contentType) {
			case "image/png" -> ".png";
			case "image/jpeg", "image/jpg" -> ".jpg";
			case "image/webp" -> ".webp";
			default -> ".img";
		};
	}

	private String normalizedUrlPrefix() {
		return urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
	}

	private int nextVersion(RestaurantProfile profile, String kind) {
		Integer current = "logo".equals(kind) ? profile.getLogoVersion() : profile.getUpiQrVersion();
		return (current == null ? 0 : current) + 1;
	}

	private void applyToProfile(RestaurantProfile profile, String kind, String url, int version) {
		long now = System.currentTimeMillis();
		if ("logo".equals(kind)) {
			profile.setLogoUrl(url);
			profile.setLogoVersion(version);
		} else {
			profile.setUpiQrUrl(url);
			profile.setUpiQrVersion(version);
		}
		profile.setUpdatedAt(now);
		profile.setServerUpdatedAt(now);
	}

	private void deleteOldVersions(Long restaurantId, String kind, int keepVersion) {
		Path dir = Paths.get(basePath, String.valueOf(restaurantId));
		if (!Files.exists(dir)) return;
		String prefix = kind + "_v";
		String keepName = prefix + keepVersion + ".webp";
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(p -> p.getFileName().toString().startsWith(prefix))
					.filter(p -> !p.getFileName().toString().equals(keepName))
					.forEach(p -> {
						try { Files.deleteIfExists(p); } catch (IOException ignored) {}
					});
		} catch (IOException e) {
			log.warn("Old-version cleanup failed for restaurant {}", restaurantId, e);
		}
	}

	private void runCwebp(Path input, Path output, boolean lossless, int quality)
			throws IOException, InterruptedException {
		List<String> cmd = new java.util.ArrayList<>();
		cmd.add(cwebpBin);
		if (lossless) {
			cmd.add("-lossless");
			cmd.add("-z");
			cmd.add("9");
		} else {
			cmd.add("-q");
			cmd.add(String.valueOf(quality));
		}
		cmd.add("-mt");
		cmd.add(input.toString());
		cmd.add("-o");
		cmd.add(output.toString());

		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		boolean finished = p.waitFor(30, TimeUnit.SECONDS);
		if (!finished) {
			p.destroyForcibly();
			throw new IOException("cwebp timed out");
		}
		if (p.exitValue() != 0) {
			String output_ = new String(p.getInputStream().readAllBytes());
			throw new IOException("cwebp failed (exit=" + p.exitValue() + "): " + output_);
		}
	}

	public record AssetUploadResult(String url, int version) {
	}
}
