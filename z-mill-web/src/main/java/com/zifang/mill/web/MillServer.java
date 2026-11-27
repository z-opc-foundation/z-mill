package com.zifang.mill.web;

import com.zifang.mill.Mill;
import com.zifang.mill.MillBatch;
import com.zifang.mill.MillChunker;
import com.zifang.mill.MillEdit;
import com.zifang.mill.MillJson;
import com.zifang.mill.MillMiniJson;
import com.zifang.mill.MillOutput;
import com.zifang.mill.MillView;
import com.zifang.mill.ocr.MillOcrs;
import com.zifang.mill.ocr.OcrConfig;
import com.zifang.util.doc.DocBlock;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/** 网页磨坊：JDK 内置 HttpServer，单页前端内嵌资源。GET / 出页面，POST /api/grind 收料出成品。 */
public final class MillServer {

    /** 单次上传上限：64MB。超出直接 413，防一个请求拖垮磨坊。 */
    static final long MAX_BODY_BYTES = 64L * 1024 * 1024;

    /** 单个网页抓取上限：10MB。 */
    static final long MAX_URL_BYTES = 10L * 1024 * 1024;

    /** 内嵌图片回传预算：单张 8MB、最多 50 张、base64 总量 30MB，超出截断并标记。 */
    static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    static final int MAX_IMAGE_COUNT = 50;
    static final long MAX_IMAGES_TOTAL_BYTES = 30L * 1024 * 1024;

    public static final int DEFAULT_PORT = 8891;

    private final Mill mill;
    private final MillEdit edit;
    private volatile HttpServer server;

    public MillServer() {
        this(new Mill());
    }

    public MillServer(Mill mill) {
        this.mill = mill;
        this.edit = new MillEdit(mill);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = DEFAULT_PORT;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[i + 1]);
            }
        }
        OcrConfig ocrConfig = MillOcrs.parseArgs(args);
        Mill mill = new Mill(MillOcrs.fromConfig(ocrConfig));
        if (ocrConfig.baseUrl() != null && !ocrConfig.baseUrl().isBlank()) {
            System.out.println("OCR 已启用：" + ocrConfig.baseUrl()
                    + "（模型 " + ocrConfig.model() + "，仅对扫描版 PDF 生效）");
        }
        MillServer millServer = new MillServer(mill);
        int bound = millServer.start(port);
        System.out.println("z-mill 资料磨坊已开张：http://localhost:" + bound);
        new CountDownLatch(1).await();
    }

    /** 绑定并开始监听；port=0 时由系统分配，返回实际端口（测试用）。 */
    public int start(int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.setExecutor(Executors.newFixedThreadPool(8));
        http.createContext("/", this::serveIndex);
        http.createContext("/api/grind", this::serveGrind);
        http.createContext("/api/grind-url", this::serveGrindUrl);
        http.createContext("/api/grind-zip", this::serveGrindZip);
        http.createContext("/api/edit-open", this::serveEditOpen);
        http.createContext("/api/edit-export", this::serveEditExport);
        http.start();
        server = http;
        return http.getAddress().getPort();
    }

    public void stop() {
        HttpServer http = server;
        if (http != null) {
            http.stop(0);
            server = null;
        }
    }

    private void serveIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "只接受 GET");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            sendError(exchange, 404, "没有这个页面");
            return;
        }
        byte[] html;
        try (InputStream in = MillServer.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                sendError(exchange, 500, "页面资源缺失");
                return;
            }
            html = in.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, html.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(html);
        }
    }

    private void serveGrind(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "只接受 POST");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
                sendError(exchange, 400, "请用 multipart/form-data 上传文件");
                return;
            }
            byte[] body = readBody(exchange);
            List<MillMultipart.Part> parts = MillMultipart.parse(body, contentType);

            List<MillMultipart.Part> files = new ArrayList<>();
            Map<String, String> fields = new LinkedHashMap<>();
            for (MillMultipart.Part part : parts) {
                if (part.filename() != null) {
                    files.add(part);
                } else {
                    fields.put(part.name(), new String(part.content(), StandardCharsets.UTF_8));
                }
            }
            if (files.isEmpty()) {
                sendError(exchange, 400, "没有收到任何文件");
                return;
            }
            List<MillView> views = parseViews(fields.getOrDefault("views", "md,json,txt,html"));
            boolean withChunks = "true".equalsIgnoreCase(fields.getOrDefault("chunks", "true"));

            StringBuilder sb = new StringBuilder("{\"results\":[");
            List<Ground> grounds = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                grounds.add(appendResult(sb,
                        files.get(i).filename() == null ? "(unnamed)" : files.get(i).filename(),
                        files.get(i).content(), views, withChunks));
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (BodyTooLarge e) {
            sendError(exchange, 413, "上传超过 " + (MAX_BODY_BYTES / 1024 / 1024) + "MB 上限");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    /** 一次成功研磨的完整留存：给 zip 端点复用。 */
    private record Ground(String base, String name, MillOutput output) {
    }

    /** 网页直磨：POST {"url":"https://..."} → 抓取 → 磨。只收 http/https，抓取上限 10MB。 */
    private void serveGrindUrl(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "只接受 POST");
                return;
            }
            String body = new String(readBody(exchange), StandardCharsets.UTF_8);
            String url = extractJsonString(body, "url");
            if (url == null || url.isBlank()) {
                sendError(exchange, 400, "缺少 url 字段");
                return;
            }
            String scheme = java.net.URI.create(url).getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                sendError(exchange, 400, "只支持 http/https 网页地址");
                return;
            }
            byte[] page = fetchUrl(url);
            StringBuilder sb = new StringBuilder("{\"results\":[");
            appendResult(sb, url, page, parseViews("md,json,txt,html"), true);
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (BodyTooLarge e) {
            sendError(exchange, 413, "网页超过 10MB 上限");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (IOException e) {
            sendError(exchange, 502, "抓取失败: " + e.getMessage());
        }
    }

    private static byte[] fetchUrl(String url) throws IOException {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL()
                .openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "z-mill/0.1");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) >= 0) {
                total += read;
                if (total > MAX_URL_BYTES) {
                    throw new BodyTooLarge();
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    /** 极简 JSON 字符串字段提取（\" 与 \\ 转义还原）；字段不存在返回 null。 */
    private static String extractJsonString(String json, String key) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private Ground appendResult(StringBuilder sb, String name, byte[] data,
                                List<MillView> views, boolean withChunks) {
        sb.append("{\"name\":").append(MillJson.escape(name));
        if (!mill.supports(data)) {
            sb.append(",\"status\":\"unsupported\"}");
            return null;
        }
        try {
            MillOutput output = mill.grind(data, name);
            sb.append(",\"status\":\"ok\",\"format\":").append(MillJson.escape(
                    output.format() == null ? "" : output.format().name()));
            List<MillChunker.Chunk> chunks = withChunks ? MillChunker.chunk(output.document()) : null;
            sb.append(",\"stats\":{\"blocks\":").append(output.document().getBlocks().size());
            if (chunks != null) {
                sb.append(",\"chunks\":").append(chunks.size());
            }
            sb.append(",\"chars\":").append(output.markdown().length()).append('}');
            sb.append(",\"outputs\":{");
            for (int i = 0; i < views.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                MillView view = views.get(i);
                sb.append(MillJson.escape(view.extension())).append(':')
                        .append(MillJson.escape(view.render(output)));
            }
            sb.append('}');
            appendImages(sb, output);
            if (chunks != null) {
                sb.append(",\"chunks\":[");
                for (int i = 0; i < chunks.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    MillChunker.Chunk chunk = chunks.get(i);
                    sb.append("{\"heading\":").append(MillJson.escape(chunk.heading()));
                    sb.append(",\"path\":[");
                    for (int j = 0; j < chunk.headingPath().size(); j++) {
                        if (j > 0) {
                            sb.append(',');
                        }
                        sb.append(MillJson.escape(chunk.headingPath().get(j)));
                    }
                    sb.append("],\"text\":").append(MillJson.escape(chunk.text())).append('}');
                }
                sb.append(']');
            }
            sb.append('}');
            return new Ground(zipSafeBase(name), name, output);
        } catch (IOException | RuntimeException e) {
            sb.append(",\"status\":\"failed\",\"error\":").append(MillJson.escape(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())).append('}');
            return null;
        }
    }

    /** 内嵌图片按预算回传（base64），超出截断并标记 imagesTruncated。 */
    private void appendImages(StringBuilder sb, MillOutput output) {
        List<DocBlock.Image> images = new ArrayList<>();
        collectImages(output.document().getBlocks(), images);
        if (images.isEmpty()) {
            sb.append(",\"images\":[]");
            return;
        }
        long total = 0;
        int emitted = 0;
        boolean truncated = false;
        sb.append(",\"images\":[");
        for (DocBlock.Image image : images) {
            if (image.data() == null || image.data().length == 0
                    || image.data().length > MAX_IMAGE_BYTES) {
                if (image.data() != null && image.data().length > MAX_IMAGE_BYTES) {
                    truncated = true;
                }
                continue;
            }
            if (emitted >= MAX_IMAGE_COUNT || total + image.data().length > MAX_IMAGES_TOTAL_BYTES) {
                truncated = true;
                break;
            }
            if (emitted > 0) {
                sb.append(',');
            }
            total += image.data().length;
            emitted++;
            sb.append("{\"name\":").append(MillJson.escape(image.name()))
                    .append(",\"mime\":").append(MillJson.escape(image.mimeType()))
                    .append(",\"b64\":\"").append(java.util.Base64.getEncoder()
                            .encodeToString(image.data())).append("\"}");
        }
        sb.append(']');
        if (truncated) {
            sb.append(",\"imagesTruncated\":true");
        }
    }

    private static void collectImages(List<DocBlock> blocks, List<DocBlock.Image> out) {
        for (DocBlock block : blocks) {
            if (block instanceof DocBlock.Image image) {
                out.add(image);
            } else if (block instanceof DocBlock.Table table) {
                for (DocBlock.Table.Row row : table.rows()) {
                    for (DocBlock.Table.Cell cell : row.cells()) {
                        collectImages(cell.blocks(), out);
                    }
                }
            } else if (block instanceof DocBlock.Slides slides) {
                for (DocBlock.Slides.Slide slide : slides.slides()) {
                    collectImages(slide.blocks(), out);
                }
            }
        }
    }

    /** zip 内的安全目录名：去扩展名 + 路径字符清洗 + 去重。 */
    private static String zipSafeBase(String name) {
        String base = MillBatch.stripExtension(name);
        String cleaned = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    /** 批量研磨打包：POST /api/grind-zip（与 /api/grind 同参）→ application/zip。 */
    private void serveGrindZip(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "只接受 POST");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
                sendError(exchange, 400, "请用 multipart/form-data 上传文件");
                return;
            }
            byte[] body = readBody(exchange);
            List<MillMultipart.Part> parts = MillMultipart.parse(body, contentType);
            List<MillMultipart.Part> files = new ArrayList<>();
            Map<String, String> fields = new LinkedHashMap<>();
            for (MillMultipart.Part part : parts) {
                if (part.filename() != null) {
                    files.add(part);
                } else {
                    fields.put(part.name(), new String(part.content(), StandardCharsets.UTF_8));
                }
            }
            if (files.isEmpty()) {
                sendError(exchange, 400, "没有收到任何文件");
                return;
            }
            List<MillView> views = parseViews(fields.getOrDefault("views", "md,json,txt,html"));
            List<String> manifest = new ArrayList<>();
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer);
            java.util.Set<String> usedBases = new java.util.HashSet<>();
            int ok = 0;
            int unsupported = 0;
            int failed = 0;
            for (MillMultipart.Part file : files) {
                String rawName = file.filename() == null ? "(unnamed)" : file.filename();
                byte[] data = file.content();
                if (!mill.supports(data)) {
                    unsupported++;
                    manifest.add("unsupported\t" + rawName);
                    continue;
                }
                try {
                    MillOutput output = mill.grind(data, rawName);
                    ok++;
                    String base = zipSafeBase(rawName);
                    String dir = base;
                    int n = 2;
                    while (!usedBases.add(dir)) {
                        dir = base + "-" + n++;
                    }
                    for (MillView view : views) {
                        zip.putNextEntry(new java.util.zip.ZipEntry(dir + "/" + base + "." + view.extension()));
                        zip.write(view.render(output).getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                    }
                    List<DocBlock.Image> images = new ArrayList<>();
                    collectImages(output.document().getBlocks(), images);
                    java.util.Set<String> usedImageNames = new java.util.HashSet<>();
                    int imageNo = 0;
                    for (DocBlock.Image image : images) {
                        if (image.data() == null || image.data().length == 0) {
                            continue;
                        }
                        String imageName = image.name() == null || image.name().isBlank()
                                ? "image" : image.name();
                        String safe = imageName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
                        String entry = safe;
                        while (!usedImageNames.add(entry)) {
                            entry = safe + "-" + (++imageNo);
                        }
                        if (!entry.contains(".")) {
                            entry = entry + ".bin";
                        }
                        zip.putNextEntry(new java.util.zip.ZipEntry(dir + "/images/" + entry));
                        zip.write(image.data());
                        zip.closeEntry();
                    }
                    manifest.add("ok\t" + rawName + " -> " + dir + "/");
                } catch (IOException | RuntimeException e) {
                    failed++;
                    manifest.add("failed\t" + rawName + ": "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
            zip.putNextEntry(new java.util.zip.ZipEntry("manifest.txt"));
            manifest.add("合计 " + files.size() + "：成功 " + ok + "，不支持 " + unsupported + "，失败 " + failed);
            for (String line : manifest) {
                zip.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            zip.closeEntry();
            zip.finish();
            byte[] bytes = buffer.toByteArray();
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"z-mill-output.zip\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (BodyTooLarge e) {
            sendError(exchange, 413, "上传超过 " + (MAX_BODY_BYTES / 1024 / 1024) + "MB 上限");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    /** 开编辑会话：multipart 单文件或 {"url":...} → 磨进内存 session，返回块结构供前端编辑。 */
    private void serveEditOpen(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "只接受 POST");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            String name;
            byte[] data;
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
                List<MillMultipart.Part> parts = MillMultipart.parse(readBody(exchange), contentType);
                MillMultipart.Part file = parts.stream()
                        .filter(p -> p.filename() != null).findFirst().orElse(null);
                if (file == null) {
                    sendError(exchange, 400, "没有收到文件");
                    return;
                }
                name = file.filename() == null ? "(unnamed)" : file.filename();
                data = file.content();
            } else {
                String body = new String(readBody(exchange), StandardCharsets.UTF_8);
                String url = extractJsonString(body, "url");
                if (url == null || url.isBlank()) {
                    sendError(exchange, 400, "请上传文件或给 url 字段");
                    return;
                }
                String scheme = java.net.URI.create(url).getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    sendError(exchange, 400, "只支持 http/https 网页地址");
                    return;
                }
                name = url;
                data = fetchUrl(url);
            }
            String sessionId = edit.open(name, data);
            MillEdit.Session session = edit.session(sessionId);
            StringBuilder sb = new StringBuilder("{\"sessionId\":").append(MillJson.escape(sessionId));
            sb.append(",\"name\":").append(MillJson.escape(name));
            sb.append(",\"status\":\"ok\",\"doc\":");
            sb.append(MillJson.toJson(session.doc()));
            sb.append('}');
            sendJson(exchange, 200, sb.toString());
        } catch (BodyTooLarge e) {
            sendError(exchange, 413, "上传超过上限");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    /** 保存编辑并导出：{"session","fmt","edits":[{"i":n,"text":"..."}]} → 文件下载。 */
    private void serveEditExport(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "只接受 POST");
                return;
            }
            String body = new String(readBody(exchange), StandardCharsets.UTF_8);
            Map<?, ?> req = (Map<?, ?>) MillMiniJson.parse(body);
            String sessionId = (String) req.get("session");
            MillEdit.Session session = edit.session(sessionId);
            if (session == null) {
                sendError(exchange, 404, "编辑会话不存在或已过期，请重新上传");
                return;
            }
            Object fmtRaw = req.get("fmt");
            String fmt = fmtRaw == null ? "md" : fmtRaw.toString();
            List<MillEdit.Edit> edits = new ArrayList<>();
            if (req.get("edits") instanceof List<?> arr) {
                for (Object o : arr) {
                    if (!(o instanceof Map<?, ?> e)) {
                        throw new IllegalArgumentException("edits 元素必须是对象");
                    }
                    if (!(e.get("i") instanceof Number n)) {
                        throw new IllegalArgumentException("edits 元素缺 i 字段");
                    }
                    Object t = e.get("text");
                    edits.add(new MillEdit.Edit(n.intValue(), t == null ? "" : t.toString()));
                }
            }
            byte[] bytes = edit.export(session, edits, fmt);
            String fileName = MillEdit.exportName(session, fmt);
            exchange.getResponseHeaders().set("Content-Type",
                    "html".equalsIgnoreCase(fmt) ? "text/html; charset=utf-8" : "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename*=UTF-8''" + java.net.URLEncoder
                            .encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
            exchange.sendResponseHeaders(200, bytes.length == 0 ? 1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (BodyTooLarge e) {
            sendError(exchange, 413, "请求超过上限");
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        }
    }

    private static List<MillView> parseViews(String raw) {
        List<MillView> views = new ArrayList<>();
        for (String token : raw.split(",")) {
            MillView view = MillView.parse(token.trim());
            if (!views.contains(view)) {
                views.add(view);
            }
        }
        return views;
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) >= 0) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new BodyTooLarge();
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int code, String message) throws IOException {
        sendJson(exchange, code, "{\"error\":" + MillJson.escape(message) + "}");
    }

    private static final class BodyTooLarge extends RuntimeException {
    }
}
