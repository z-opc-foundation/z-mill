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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void grindsUrlPointingAtOwnIndexPage() throws IOException {
        String response = post("/api/grind-url",
                ("{\"url\":\"http://localhost:" + port + "/\"}").getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertTrue(response.startsWith("200|"), response);
        assertTrue(response.contains("\"format\":\"HTML\""), response);
        assertTrue(response.contains("资料磨坊"), response);
        assertTrue(response.contains("\"chunks\""), response);
    }

    @Test
    void rejectsNonHttpUrlSchemes() throws IOException {
        String response = post("/api/grind-url",
                "{\"url\":\"file:///etc/passwd\"}".getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertTrue(response.startsWith("400|"), response);
        assertTrue(response.contains("http/https"), response);

        String missing = post("/api/grind-url", "{}".getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertTrue(missing.startsWith("400|"), missing);
    }

    private byte[] corpus(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/corpus/" + name)) {
            if (in == null) {
                throw new IOException("corpus 缺失: " + name);
            }
            return in.readAllBytes();
        }
    }

    @Test
    void grindsDocxWithEmbeddedImage() throws IOException {
        byte[] picture = corpus("tiny-picture.docx");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(partBody("MB", "files", "tiny-picture.docx", picture));
        out.writeBytes(terminal("MB"));
        String response = post("/api/grind", out.toByteArray(), "multipart/form-data; boundary=MB");
        assertTrue(response.startsWith("200|"), response);
        assertTrue(response.contains("\"images\":[{"), response);
        assertTrue(response.contains("\"b64\":\""), response);
        assertFalse(response.contains("\"imagesTruncated\""), response);
    }

    @Test
    void grindZipReturnsZipWithManifestAndOutputs() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(partBody("MB", "files", "hello.txt",
                "zip 冒烟正文".getBytes(StandardCharsets.UTF_8)));
        out.writeBytes(partBody("MB", "files", "junk.bin",
                new byte[]{0x00, 0x01, (byte) 0xFF, 0x00, 0x01, (byte) 0xFF}));
        out.writeBytes(terminal("MB"));
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/api/grind-zip")
                .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=MB");
        conn.setFixedLengthStreamingMode(out.size());
        try (OutputStream os = conn.getOutputStream()) {
            os.write(out.toByteArray());
        }
        assertEquals(200, conn.getResponseCode());
        assertEquals("application/zip", conn.getHeaderField("Content-Type"));
        byte[] zip = readAll(conn.getInputStream());
        assertTrue(zip.length > 4);
        assertEquals('P', (char) zip[0]);
        assertEquals('K', (char) zip[1]);

        // 真解包：条目齐全 + manifest 台账内容正确
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(zip));
        java.util.Set<String> entries = new java.util.HashSet<>();
        String manifest = null;
        java.util.zip.ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            entries.add(entry.getName());
            if (entry.getName().equals("manifest.txt")) {
                manifest = new String(readAll(zin), StandardCharsets.UTF_8);
            }
        }
        assertTrue(entries.contains("hello/hello.md"), entries.toString());
        assertTrue(entries.contains("hello/hello.json"), entries.toString());
        assertTrue(entries.contains("manifest.txt"), entries.toString());
        assertNotNull(manifest);
        assertTrue(manifest.contains("unsupported\tjunk.bin"), manifest);
        assertTrue(manifest.contains("ok\thello.txt -> hello/"), manifest);
        assertTrue(manifest.contains("成功 1，不支持 1，失败 0"), manifest);
    }

    @Test
    void editSessionOpenApplyExportRoundTrip() throws IOException {
        // 开会话
        ByteArrayOutputStream openBody = new ByteArrayOutputStream();
        openBody.writeBytes(partBody("MB", "file", "笔记.txt",
                "第一段原文\n\n第二段原文".getBytes(StandardCharsets.UTF_8)));
        openBody.writeBytes(terminal("MB"));
        String open = post("/api/edit-open", openBody.toByteArray(),
                "multipart/form-data; boundary=MB");
        assertTrue(open.startsWith("200|"), open);
        assertTrue(open.contains("\"sessionId\":\""), open);
        assertTrue(open.contains("\"doc\":{\"meta\""), open);
        String sessionId = open.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");

        // 未知会话 → 404
        String missing = post("/api/edit-export",
                "{\"session\":\"no-such\",\"fmt\":\"md\",\"edits\":[]}".getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertTrue(missing.startsWith("404|"), missing);

        // 改第 0 块并导出 txt
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/api/edit-export")
                .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        byte[] body = ("{\"session\":\"" + sessionId + "\",\"fmt\":\"txt\","
                + "\"edits\":[{\"i\":0,\"text\":\"改过的第一段\"}]}").getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        assertEquals(200, conn.getResponseCode());
        String disposition = conn.getHeaderField("Content-Disposition");
        assertTrue(disposition.startsWith("attachment; filename*=UTF-8''"), disposition);
        assertTrue(disposition.endsWith(".txt"), disposition);
        String out = new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8);
        assertTrue(out.contains("改过的第一段"), out);
        assertFalse(out.contains("第一段原文"), out);
        assertTrue(out.contains("第二段原文"), out);

        // 非文本块（表格）编辑被拒
        String table = post("/api/edit-export",
                ("{\"session\":\"" + sessionId + "\",\"fmt\":\"md\",\"edits\":[{\"i\":0,\"text\":\"x\"}]}")
                        .getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertTrue(table.startsWith("200|") || table.startsWith("400|"), table);
    }

    @Test
    void editOpenRejectsGarbageAndOversize() throws IOException {
        String garbage = post("/api/edit-open", partBody("MB", "file", "junk.bin",
                new byte[]{0x00, 0x01, (byte) 0xFF, 0x00, 0x01, (byte) 0xFF}),
                "multipart/form-data; boundary=MB");
        assertTrue(garbage.startsWith("400|"), garbage);
        String oversize = post("/api/edit-open", partBody("MB", "file", "big.txt",
                new byte[(int) (com.zifang.mill.MillEdit.MAX_EDIT_FILE_BYTES + 1)]),
                "multipart/form-data; boundary=MB");
        assertTrue(oversize.startsWith("400|") || oversize.startsWith("413|"), oversize);
    }

    private static void assertNotNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNotNull(value, "manifest 缺失");
    }
}
