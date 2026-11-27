package com.zifang.mill.ocr;

import com.zifang.mill.MillJson;
import com.zifang.mill.MillMiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 视觉模型 OCR：把图片以 dataURL 送 OpenAI 兼容的 chat/completions（默认 z-llm-gateway），
 * 取回纯文本转写。任何失败都返回 null（调用方保留图片块），不抛异常。
 */
public final class LlmVisionOcr implements MillOcr {

    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final int timeoutMs;
    private final String prompt;
    private final HttpClient client;

    public LlmVisionOcr(OcrConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(config.timeoutMillis(), 10_000)))
                .build());
    }

    LlmVisionOcr(OcrConfig config, HttpClient client) {
        String base = config.baseUrl().trim();
        this.endpoint = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/v1/chat/completions";
        this.model = config.model() == null || config.model().isBlank()
                ? OcrConfig.DEFAULT_MODEL : config.model();
        this.apiKey = config.apiKey();
        this.timeoutMs = config.timeoutMillis();
        this.prompt = config.prompt() == null || config.prompt().isBlank()
                ? OcrConfig.DEFAULT_PROMPT : config.prompt();
        this.client = client;
    }

    @Override
    public String transcribe(byte[] image, String mimeType) {
        String dataUrl = "data:" + (mimeType == null || mimeType.isBlank()
                ? "image/png" : mimeType) + ";base64,"
                + Base64.getEncoder().encodeToString(image);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequest(dataUrl),
                        StandardCharsets.UTF_8));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
        if (response.statusCode() != 200) {
            return null;
        }
        return extractContent(response.body());
    }

    private String buildRequest(String dataUrl) {
        return "{\"model\":" + MillJson.escape(model) + ",\"temperature\":0,\"messages\":["
                + "{\"role\":\"user\",\"content\":["
                + "{\"type\":\"image_url\",\"image_url\":{\"url\":" + MillJson.escape(dataUrl) + "}},"
                + "{\"type\":\"text\",\"text\":" + MillJson.escape(prompt) + "}]}]}";
    }

    static String extractContent(String json) {
        Object parsed;
        try {
            parsed = MillMiniJson.parse(json);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            return null;
        }
        Object choices = root.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        if (!(list.get(0) instanceof Map<?, ?> choice)) {
            return null;
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> m)) {
            return null;
        }
        Object content = m.get("content");
        if (!(content instanceof String text)) {
            return null;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}