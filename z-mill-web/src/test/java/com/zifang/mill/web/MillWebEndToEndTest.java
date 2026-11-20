package com.zifang.mill.web;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 进程内起真服务器，HTTP 口径端到端。 */
class MillWebEndToEndTest {

    private static MillServer server;
    private static int port;

    @BeforeAll
    static void up() throws IOException {
        server = new MillServer();
        port = server.start(0);
    }

    @AfterAll
    static void down() {
        server.stop();
    }

    private static String get(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        assertEquals(200, conn.getResponseCode(), path);
        return new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
    }

    private static String post(String path, byte[] body, String contentType) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        InputStream stream = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        return conn.getResponseCode() + "|" + new String(readAll(stream), StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) >= 0) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /** 一个不带终止线的 part。 */
    private static byte[] partBody(String boundary, String name, String filename, byte[] content) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(head.getBytes(StandardCharsets.UTF_8));
        out.writeBytes(content);
        out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static byte[] terminal(String boundary) {
        return ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void servesIndexPage() throws IOException {
        String html = get("/");
        assertTrue(html.contains("z-mill 资料磨坊"), html.substring(0, 200));
    }

    @Test
    void grindsTextAndRejectsGarbageInOneRequest() throws IOException {
        String boundary = "MillTestBoundary";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(partBody(boundary, "files", "hello.txt",
                "你好磨坊\n\n第二段".getBytes(StandardCharsets.UTF_8)));
        out.writeBytes(partBody(boundary, "files", "junk.bin",
                new byte[]{0x00, 0x01, (byte) 0xFF, 0x00, 0x01, (byte) 0xFF}));
        out.writeBytes(terminal(boundary));
        String response = post("/api/grind", out.toByteArray(), "multipart/form-data; boundary=" + boundary);
        assertTrue(response.startsWith("200|"), response);
        assertTrue(response.contains("\"status\":\"ok\""), response);
        assertTrue(response.contains("\"format\":\"TXT\""), response);
        assertTrue(response.contains("你好磨坊"), response);
        assertTrue(response.contains("\"status\":\"unsupported\""), response);
        assertTrue(response.contains("\"chunks\":1"), response);
    }

    @Test
    void rejectsMissingFilesWith400() throws IOException {
        byte[] body = "--B\r\nContent-Disposition: form-data; name=\"views\"\r\n\r\nmd\r\n--B--\r\n"
                .getBytes(StandardCharsets.UTF_8);
        String response = post("/api/grind", body, "multipart/form-data; boundary=B");
        assertTrue(response.startsWith("400|"), response);
        assertTrue(response.contains("没有收到任何文件"), response);
    }

    @Test
    void rejectsNonMultipartWith400() throws IOException {
        String response = post("/api/grind", "{}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertTrue(response.startsWith("400|"), response);
    }

    @Test
    void unknownPathAndMethodReturnErrorCodes() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/nope").openConnection();
        assertEquals(404, conn.getResponseCode());
        String response = post("/api/grind", new byte[0], "text/plain");
        assertTrue(response.startsWith("400|"), response);
    }
}
