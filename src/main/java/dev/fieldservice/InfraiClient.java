package dev.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class InfraiClient implements FieldServiceGateway {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String experimentValue(String experimentKey) {
        return get("/v1/flags/get_value/" + encode(experimentKey)).dataScalar("default_value");
    }

    @Override
    public String technicianFollowUpHistory() {
        return get("/v1/logs/search").dataJson();
    }

    private Envelope get(String path) {
        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Envelope envelope = Envelope.parse(response.body());
                if (response.statusCode() == 429) {
                    pause(retryDelayMillis(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiException(envelope.errorCode(), envelope.errorJson(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new InfraiException("HTTP_" + response.statusCode(), envelope.errorJson(), response.statusCode());
                }
                return envelope;
            } catch (IOException e) {
                throw new InfraiException("TRANSPORT", e.getMessage(), 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("INTERRUPTED", e.getMessage(), 0);
            }
        }
        throw new InfraiException("RATE_LIMITED", "Retry budget exhausted", 429);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiClient::retryAfterMillis)
                .orElse(250L * (1L << attempt));
    }

    private static long retryAfterMillis(String value) {
        try {
            return Math.max(0, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            return 1000L;
        }
    }

    private static void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static final class Envelope {
        private final String json;
        private final boolean ok;

        private Envelope(String json, boolean ok) {
            this.json = json;
            this.ok = ok;
        }

        static Envelope parse(String json) {
            if (json == null || json.isBlank() || json.charAt(0) != '{') {
                throw new InfraiException("INVALID_ENVELOPE", "Response is not a JSON object", 0);
            }
            String okValue = scalar(json, "ok");
            if (!"true".equals(okValue) && !"false".equals(okValue)) {
                throw new InfraiException("INVALID_ENVELOPE", "Response has no boolean ok field", 0);
            }
            return new Envelope(json, Boolean.parseBoolean(okValue));
        }

        boolean ok() { return ok; }
        String dataJson() { return value(json, "data"); }
        String errorJson() { return value(json, "error"); }
        String errorCode() {
            String error = errorJson();
            String code = scalar(error, "code");
            return code == null ? "INFRAI_REJECTED" : code;
        }
        String dataScalar(String field) {
            String result = scalar(dataJson(), field);
            if (result == null) throw new InfraiException("INVALID_ENVELOPE", "Missing data field: " + field, 0);
            return result;
        }

        private static String scalar(String source, String key) {
            String raw = value(source, key);
            if (raw == null) return null;
            if (raw.startsWith("\"")) return unescape(raw.substring(1, raw.length() - 1));
            return raw;
        }

        private static String value(String source, String key) {
            if (source == null) return null;
            String marker = "\"" + key + "\"";
            int keyAt = source.indexOf(marker);
            if (keyAt < 0) return null;
            int colon = source.indexOf(':', keyAt + marker.length());
            if (colon < 0) return null;
            int start = colon + 1;
            while (start < source.length() && Character.isWhitespace(source.charAt(start))) start++;
            if (start >= source.length()) return null;
            char first = source.charAt(start);
            if (first == '"') {
                boolean escaped = false;
                for (int i = start + 1; i < source.length(); i++) {
                    char c = source.charAt(i);
                    if (c == '"' && !escaped) return source.substring(start, i + 1);
                    escaped = c == '\\' && !escaped;
                    if (c != '\\') escaped = false;
                }
                return null;
            }
            if (first == '{' || first == '[') {
                char close = first == '{' ? '}' : ']';
                int depth = 0;
                boolean quoted = false;
                boolean escaped = false;
                for (int i = start; i < source.length(); i++) {
                    char c = source.charAt(i);
                    if (c == '"' && !escaped) quoted = !quoted;
                    if (!quoted) {
                        if (c == first) depth++;
                        if (c == close && --depth == 0) return source.substring(start, i + 1);
                    }
                    escaped = c == '\\' && !escaped;
                    if (c != '\\') escaped = false;
                }
                return null;
            }
            int end = start;
            while (end < source.length() && ",}]".indexOf(source.charAt(end)) < 0) end++;
            return source.substring(start, end).trim();
        }

        private static String unescape(String value) {
            return value.replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}
