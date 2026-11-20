package com.zifang.mill.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 手写 multipart/form-data 解析：字节级切分 boundary，头 ISO-8859-1、正文留给调用方按需解码。 */
public final class MillMultipart {

    /** 一个 part：表单字段名、上传文件名（字段 part 为 null）、原始字节。 */
    public record Part(String name, String filename, byte[] content) {
    }

    private MillMultipart() {
    }

    public static List<Part> parse(byte[] body, String contentType) {
        String boundary = boundaryOf(contentType);
        if (boundary == null) {
            throw new IllegalArgumentException("Content-Type 缺 boundary");
        }
        // RFC 2046：分隔线是 CRLF + "--boundary"；正文里的裸 boundary 字样不带 CRLF，不算线
        String open = "--" + boundary;
        byte[] openBytes = open.getBytes(StandardCharsets.ISO_8859_1);
        byte[] crlfOpen = ("\r\n" + open).getBytes(StandardCharsets.ISO_8859_1);
        List<Integer> marks = new ArrayList<>();
        if (startsWith(body, openBytes, 0)) {
            marks.add(0);
        }
        int from = 0;
        int at;
        while ((at = indexOf(body, crlfOpen, from)) >= 0) {
            marks.add(at + 2);
            from = at + 2;
        }
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i + 1 < marks.size(); i++) {
            int start = marks.get(i) + open.length();
            if (start < body.length && body[start] == '-') {
                break; // "--boundary--" 终止线
            }
            if (start + 2 <= body.length && body[start] == '\r' && body[start + 1] == '\n') {
                start += 2;
            }
            int end = marks.get(i + 1);
            if (end - 2 >= start && body[end - 2] == '\r' && body[end - 1] == '\n') {
                end -= 2;
            }
            if (end > start) {
                Part part = part(body, start, end);
                if (part != null) {
                    parts.add(part);
                }
            }
        }
        return parts;
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static Part part(byte[] body, int start, int end) {
        int split = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), start);
        if (split < 0 || split + 4 > end) {
            return null;
        }
        String headers = new String(body, start, split + 4 - start, StandardCharsets.ISO_8859_1);
        Map<String, String> headerMap = new HashMap<>();
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headerMap.put(line.substring(0, colon).trim().toLowerCase(),
                        line.substring(colon + 1).trim());
            }
        }
        String disposition = headerMap.get("content-disposition");
        if (disposition == null) {
            return null;
        }
        String name = dispositionParam(disposition, "name");
        String filename = dispositionParam(disposition, "filename");
        byte[] content = new byte[end - split - 4];
        System.arraycopy(body, split + 4, content, 0, content.length);
        return new Part(name, filename, content);
    }

    /** content-disposition 里带引号或裸值的参数。 */
    private static String dispositionParam(String disposition, String key) {
        int at = disposition.indexOf(key + "=\"");
        if (at >= 0) {
            int from = at + key.length() + 2;
            int to = disposition.indexOf('"', from);
            return to < 0 ? null : disposition.substring(from, to);
        }
        at = disposition.indexOf(key + "=");
        if (at >= 0) {
            int from = at + key.length() + 1;
            int to = disposition.indexOf(';', from);
            return (to < 0 ? disposition.substring(from) : disposition.substring(from, to)).trim();
        }
        return null;
    }

    private static String boundaryOf(String contentType) {
        for (String piece : contentType.split(";")) {
            String p = piece.trim();
            if (p.toLowerCase().startsWith("boundary=")) {
                String value = p.substring("boundary=".length());
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static int indexOf(byte[] data, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
