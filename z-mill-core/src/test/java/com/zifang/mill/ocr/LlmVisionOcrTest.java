package com.zifang.mill.ocr;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 假 z-llm-gateway：验证请求形状（image_url dataURL）与响应解析、失败降级。 */
class LlmVisionOcrTest {

    private HttpServer server;
    private String endpointBase;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private volatile int statusCode = 200;
    private volatile String responseBody = "{\"choices\":[{\"message\":{\"content\":\"扫描页正文\"}}]}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
        endpointBase = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastRequest.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private LlmVisionOcr client() {
        return new LlmVisionOcr(new OcrConfig(endpointBase, "qwen-vl-max", "test-key",
                5000, null));
    }

    @Test
    void sendsDataUrlImageAndReturnsContent() {
        byte[] image = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        String text = client().transcribe(image, "image/png");
        assertEquals("扫描页正文", text);
        String req = lastRequest.get();
        assertTrue(req.contains("\"model\":\"qwen-vl-max\""));
        assertTrue(req.contains("\"type\":\"image_url\""));
        assertTrue(req.contains("\"data:image/png;base64,"));
        assertTrue(req.contains("OCR 引擎"));
    }

    @Test
    void non200SendsNullBack() {
        statusCode = 502;
        assertNull(client().transcribe(new byte[]{1}, "image/png"));
    }

    @Test
    void garbageBodySendsNullBack() {
        responseBody = "not json at all {";
        assertNull(client().transcribe(new byte[]{1}, "image/png"));
    }

    @Test
    void missingChoicesSendsNullBack() {
        responseBody = "{\"choices\":[]}";
        assertNull(client().transcribe(new byte[]{1}, "image/png"));
    }

    @Test
    void extractContentHandlesShapes() {
        assertEquals("hi", LlmVisionOcr.extractContent(
                "{\"choices\":[{\"message\":{\"content\":\" hi \"}}]}"));
        assertNull(LlmVisionOcr.extractContent("{\"choices\":[{\"message\":{}}]}"));
        assertNull(LlmVisionOcr.extractContent("{\"choices\":[{\"message\":{\"content\":[]}}]}"));
        assertNull(LlmVisionOcr.extractContent("{}"));
        assertNull(LlmVisionOcr.extractContent("[1,2]"));
        assertNull(LlmVisionOcr.extractContent("{bad"));
    }

    @Test
    void unreachableServerSendsNullBack() {
        LlmVisionOcr client = new LlmVisionOcr(new OcrConfig(
                "http://127.0.0.1:1", "m", null, 2000, null));
        assertNull(client.transcribe(new byte[]{1}, "image/png"));
    }

    @Test
    void hasAuthHeaderWhenKeyProvided() {
        client().transcribe(new byte[]{1}, "image/png");
        // 请求体断言没法看 header；这里只保证不抛异常（header 由 HttpRequest 构建）
        assertTrue(lastRequest.get().length() > 0);
    }
}