package com.zifang.mill.web;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MillMultipartTest {

    private static final String BOUNDARY = "JavaBoundary42";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) {
            total += a.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }

    @Test
    void splitsFieldsAndFiles() {
        byte[] body = concat(
                bytes("--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"views\"\r\n\r\n"
                        + "md,json\r\n"
                        + "--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"files\"; filename=\"a.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\n"
                        + "第一行\n第二行\r\n"
                        + "--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"files\"; filename=\"b.bin\"\r\n\r\n"),
                new byte[]{0x00, 0x01, (byte) 0xFF, 0x00},
                bytes("\r\n--" + BOUNDARY + "--\r\n"));

        List<MillMultipart.Part> parts = MillMultipart.parse(body,
                "multipart/form-data; boundary=" + BOUNDARY);
        assertEquals(3, parts.size());
        assertNull(parts.get(0).filename());
        assertEquals("views", parts.get(0).name());
        assertEquals("md,json", new String(parts.get(0).content(), StandardCharsets.UTF_8));
        assertEquals("a.txt", parts.get(1).filename());
        assertEquals("第一行\n第二行", new String(parts.get(1).content(), StandardCharsets.UTF_8));
        assertEquals("b.bin", parts.get(2).filename());
        assertEquals(4, parts.get(2).content().length);
    }

    @Test
    void acceptsQuotedBoundaryAndRejectsMissing() {
        byte[] body = bytes("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"x\"\r\n\r\n1\r\n"
                + "--" + BOUNDARY + "--\r\n");
        List<MillMultipart.Part> parts = MillMultipart.parse(body,
                "multipart/form-data; boundary=\"" + BOUNDARY + "\"");
        assertEquals(1, parts.size());

        assertThrows(IllegalArgumentException.class,
                () -> MillMultipart.parse(body, "multipart/form-data"));
    }

    @Test
    void contentContainingBoundaryTextSurvives() {
        // 正文里出现 boundary 字样但不是真分隔线（前面没有 \r\n），不能切断
        byte[] body = bytes("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\"c.txt\"\r\n\r\n"
                + "text mentions --" + BOUNDARY + " inline\r\n"
                + "--" + BOUNDARY + "--\r\n");
        List<MillMultipart.Part> parts = MillMultipart.parse(body,
                "multipart/form-data; boundary=" + BOUNDARY);
        assertEquals(1, parts.size());
        assertEquals("text mentions --" + BOUNDARY + " inline",
                new String(parts.get(0).content(), StandardCharsets.UTF_8));
    }
}
