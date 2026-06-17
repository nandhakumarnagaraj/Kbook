package com.khanabook.saas.service;

import com.khanabook.saas.entity.RestaurantProfile;
import com.khanabook.saas.entity.EasebuzzSubMerchant;
import com.khanabook.saas.repository.EasebuzzSubMerchantRepository;
import com.khanabook.saas.repository.RestaurantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
	private final EasebuzzSubMerchantRepository subMerchantRepo;

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
		// Logos: lossless WebP — logos have sharp edges/solid colors, lossy creates artifacts.
		return uploadAsset(restaurantId, file, "logo", true, 85);
	}

	@Transactional
	public void deleteLogo(Long restaurantId) {
		deleteAsset(restaurantId, "logo");
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
			boolean hasCwebp = cwebpBin != null && !cwebpBin.isBlank();
			String ext = hasCwebp ? ".webp" : guessExtension(file.getContentType());
			String filename = kind + "_v" + newVersion + ext;
			Path target = Paths.get(basePath, String.valueOf(restaurantId), filename);
			Files.createDirectories(target.getParent());

			if (hasCwebp) {
				runCwebp(tmp, target, lossless, quality);
			} else {
				log.warn("cwebp not configured; copying {} -> {}", tmp, target);
				Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}

			if (!Files.exists(target) || Files.size(target) == 0) {
				throw new IllegalStateException("Upload produced no output");
			}

			String url = resolveCdnUrl(restaurantId, filename);
			applyToProfile(profile, kind, url, newVersion);
			restaurantProfileRepository.save(profile);

			deleteOldVersions(restaurantId, kind, newVersion);

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
			case "application/pdf" -> ".pdf";
			default -> ".img";
		};
	}

	private String resolveCdnUrl(Long restaurantId, String filename) {
		try {
			ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			if (attrs != null) {
				jakarta.servlet.http.HttpServletRequest req = attrs.getRequest();
				String scheme = req.getScheme();
				String host = req.getServerName();
				int port = req.getServerPort();
				String contextPath = req.getContextPath();
				String portStr = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443) ? "" : ":" + port;
				return scheme + "://" + host + portStr + "/cdn/" + restaurantId + "/" + filename;
			}
		} catch (Exception e) {
			log.debug("Could not resolve CDN URL from request context, falling back to configured prefix");
		}
		return normalizedUrlPrefix() + restaurantId + "/" + filename;
	}

	private String normalizedUrlPrefix() {
		return urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
	}

	private int nextVersion(RestaurantProfile profile, String kind) {
		Integer current = profile.getLogoVersion();
		return (current == null ? 0 : current) + 1;
	}

	private void applyToProfile(RestaurantProfile profile, String kind, String url, int version) {
		long now = System.currentTimeMillis();
		profile.setLogoUrl(url);
		profile.setLogoVersion(version);
		profile.setUpdatedAt(now);
		profile.setServerUpdatedAt(now);
		profile.setDeviceId("server");
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
		if (cwebpBin == null || cwebpBin.isBlank()) {
			log.warn("cwebp not configured; copying {} -> {}", input, output);
			Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
			return;
		}
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

	@Transactional
	public AssetUploadResult uploadKycDocument(Long restaurantId, String docType, MultipartFile file) {
		validateKycFile(file);

		Path tmp = null;
		try {
			tmp = saveToTmp(file);

			EasebuzzSubMerchant sm = subMerchantRepo.findByRestaurantId(restaurantId)
					.orElseThrow(() -> new IllegalStateException("Sub-merchant not configured for restaurant: " + restaurantId));
			
			int currentVersion = 0;
			String currentUrl = null;
			switch (docType) {
				case "id_proof": currentUrl = sm.getIdProofUrl(); break;
				case "bank_proof": currentUrl = sm.getBankProofUrl(); break;
				case "business_proof_1": currentUrl = sm.getBusinessProof1Url(); break;
				case "business_proof_2": currentUrl = sm.getBusinessProof2Url(); break;
			}
			if (currentUrl != null) {
				currentVersion = parseVersionFromUrl(currentUrl);
			}
			int newVersion = currentVersion + 1;

			String contentType = file.getContentType();
			boolean isPdf = contentType != null && contentType.equalsIgnoreCase("application/pdf");
			String ext = isPdf ? ".pdf" : (cwebpBin != null && !cwebpBin.isBlank() ? ".webp" : guessExtension(contentType));
			String filename = "kyc_" + docType + "_v" + newVersion + ext;

			Path target = Paths.get(basePath, String.valueOf(restaurantId), filename);
			Files.createDirectories(target.getParent());

			if (isPdf) {
				Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			} else if (cwebpBin != null && !cwebpBin.isBlank()) {
				runCwebp(tmp, target, false, 80);
			} else {
				Files.copy(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}

			if (!Files.exists(target) || Files.size(target) == 0) {
				throw new IllegalStateException("Upload produced no output");
			}

			String url = resolveCdnUrl(restaurantId, filename);
			
			switch (docType) {
				case "id_proof": sm.setIdProofUrl(url); break;
				case "bank_proof": sm.setBankProofUrl(url); break;
				case "business_proof_1": sm.setBusinessProof1Url(url); break;
				case "business_proof_2": sm.setBusinessProof2Url(url); break;
				default: throw new IllegalArgumentException("Unknown KYC document type: " + docType);
			}
			sm.setUpdatedAt(System.currentTimeMillis());
			subMerchantRepo.save(sm);

			deleteOldKycVersions(restaurantId, docType, newVersion);

			log.info("Uploaded KYC document {} for restaurant {} -> {}", docType, restaurantId, url);
			return new AssetUploadResult(url, newVersion);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("KYC document upload failed", e);
		} catch (IOException e) {
			throw new RuntimeException("KYC document upload failed", e);
		} finally {
			if (tmp != null) {
				try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
			}
		}
	}

	private void validateKycFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}
		if (file.getSize() > maxUploadBytes) {
			throw new IllegalArgumentException("File too large; max " + maxUploadBytes + " bytes");
		}
		String contentType = file.getContentType();
		if (contentType == null || (!contentType.startsWith("image/") && !contentType.equalsIgnoreCase("application/pdf"))) {
			throw new IllegalArgumentException("Only PDF and image uploads are allowed");
		}
	}

	private int parseVersionFromUrl(String url) {
		if (url == null) return 0;
		try {
			int idx = url.lastIndexOf("_v");
			if (idx != -1) {
				int dotIdx = url.indexOf(".", idx);
				String vStr = dotIdx != -1 ? url.substring(idx + 2, dotIdx) : url.substring(idx + 2);
				return Integer.parseInt(vStr);
			}
		} catch (Exception ignored) {}
		return 0;
	}

	private void deleteOldKycVersions(Long restaurantId, String docType, int keepVersion) {
		Path dir = Paths.get(basePath, String.valueOf(restaurantId));
		if (!Files.exists(dir)) return;
		String prefix = "kyc_" + docType + "_v";
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(p -> p.getFileName().toString().startsWith(prefix))
					.filter(p -> !p.getFileName().toString().startsWith(prefix + keepVersion + "."))
					.forEach(p -> {
						try { Files.deleteIfExists(p); } catch (IOException ignored) {}
					});
		} catch (IOException e) {
			log.warn("Old KYC version cleanup failed for restaurant {}", restaurantId, e);
		}
	}
}
