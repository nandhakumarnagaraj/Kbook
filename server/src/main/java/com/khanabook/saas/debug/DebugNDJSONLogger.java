package com.khanabook.saas.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal NDJSON logger for DEBUG MODE.
 * Writes to the host path configured for this session.
 *
 * NOTE: Do not log secrets/tokens/PII here.
 */
public final class DebugNDJSONLogger {
	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("debug.ndjson.enabled", "false"));
	private static final Path LOG_PATH = Paths.get(System.getProperty("debug.ndjson.path", "logs/debug.log"));

	private DebugNDJSONLogger() {}

	public static void log(
			String runId,
			String hypothesisId,
			String location,
			String message,
			Map<String, ?> data
	) {
		if (!ENABLED) return;
		try {
			long ts = System.currentTimeMillis();
			String id = "log_" + ts + "_" + UUID.randomUUID();

			Path parent = LOG_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}

			StringBuilder sb = new StringBuilder(512);
			sb.append('{');
			sb.append("\"id\":\"").append(escapeJson(id)).append('"');
			sb.append(",\"timestamp\":").append(ts);
			sb.append(",\"runId\":\"").append(escapeJson(runId)).append('"');
			sb.append(",\"hypothesisId\":\"").append(escapeJson(hypothesisId)).append('"');
			sb.append(",\"location\":\"").append(escapeJson(location)).append('"');
			sb.append(",\"message\":\"").append(escapeJson(message)).append('"');
			sb.append(",\"data\":").append(toJsonObject(data));
			sb.append('}');
			sb.append('\n');

			Files.write(LOG_PATH, sb.toString().getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// Intentionally swallow: debug logging must never break production code paths.
			// But expose the failure so we can see runtime evidence in the backend console.
			System.err.println("[DebugNDJSONLogger] Failed to write " + LOG_PATH + ": " + ignored.getMessage());
		}
	}

	private static String toJsonObject(Map<String, ?> data) {
		if (data == null || data.isEmpty()) {
			return "{}";
		}
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		boolean first = true;
		for (Map.Entry<String, ?> e : data.entrySet()) {
			if (e.getKey() == null) continue;
			if (!first) sb.append(',');
			first = false;
			sb.append('"').append(escapeJson(e.getKey())).append('"').append(':');
			sb.append(toJsonValue(e.getValue()));
		}
		sb.append('}');
		return sb.toString();
	}

	private static String toJsonValue(Object v) {
		if (v == null) return "null";
		if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
		return "\"" + escapeJson(String.valueOf(v)) + "\"";
	}

	private static String escapeJson(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}
}

