package com.zifang.mill;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocConverter;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocSpan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线编辑会话：开 session 时磨一次进内存，客户端按块序号提交文本修改，
 * 导出时先套用修改再经 DocConverter 序列化。轻量档只支持纯文本块的编辑，
 * 编辑会丢弃该块内的行内样式（加粗/颜色等）。
 */
public final class MillEdit {

    /** 编辑会话只收小文件。 */
    public static final long MAX_EDIT_FILE_BYTES = 16L * 1024 * 1024;
    /** 最多同时留存 8 个会话，超出按最久未用逐出。 */
    public static final int MAX_SESSIONS = 8;
    /** 会话闲置 2 小时后过期。 */
    static final long TTL_MS = 2L * 60 * 60 * 1000;
    /** 一次导出允许携带的修改条数与单块文本上限。 */
    public static final int MAX_EDITS = 5000;
    public static final int MAX_EDIT_TEXT_CHARS = 200_000;

    /** 一处修改：blocks[index] 的文本替换为 text。 */
    public record Edit(int index, String text) {
    }

    public static final class Session {
        private final String name;
        private final DocDocument doc;
        private long touched;

        Session(String name, DocDocument doc) {
            this.name = name;
            this.doc = doc;
            this.touched = System.currentTimeMillis();
        }

        public String name() {
            return name;
        }

        public DocDocument doc() {
            return doc;
        }

        long touched() {
            return touched;
        }

        void touch() {
            touched = System.currentTimeMillis();
        }
    }

    private final Mill mill;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public MillEdit(Mill mill) {
        this.mill = mill;
    }

    public MillEdit() {
        this(new Mill());
    }

    /** 磨一份进会话池；返回会话 id。 */
    public synchronized String open(String name, byte[] data) throws IOException {
        if (data.length > MAX_EDIT_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "文件超过 " + (MAX_EDIT_FILE_BYTES / 1024 / 1024) + "MB，编辑仅支持小文件");
        }
        if (!mill.supports(data)) {
            throw new IllegalArgumentException("嗅探不出已知格式，无法编辑");
        }
        MillOutput output = mill.grind(data, name);
        evict();
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(name, output.document()));
        return id;
    }

    public Session session(String id) {
        if (id == null) {
            return null;
        }
        Session s = sessions.get(id);
        if (s != null && System.currentTimeMillis() - s.touched() > TTL_MS) {
            sessions.remove(id);
            return null;
        }
        return s;
    }

    public void close(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    private void evict() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.touched() > TTL_MS);
        while (sessions.size() >= MAX_SESSIONS) {
            sessions.values().stream()
                    .min(Comparator.comparingLong(Session::touched))
                    .ifPresent(oldest -> sessions.remove(idOf(oldest)));
        }
    }

    private String idOf(Session target) {
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            if (e.getValue() == target) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 套用修改：返回实际改动的块数。只接受纯文本块；越界或类型不符抛 IllegalArgumentException。 */
    public int apply(Session session, List<Edit> edits) {
        if (edits == null || edits.isEmpty()) {
            return 0;
        }
        if (edits.size() > MAX_EDITS) {
            throw new IllegalArgumentException("修改条数超过 " + MAX_EDITS + " 上限");
        }
        List<DocBlock> blocks = session.doc().getBlocks();
        int applied = 0;
        for (Edit edit : edits) {
            if (edit.index() < 0 || edit.index() >= blocks.size()) {
                throw new IllegalArgumentException("块序号越界: " + edit.index());
            }
            String text = edit.text() == null ? "" : edit.text();
            if (text.length() > MAX_EDIT_TEXT_CHARS) {
                throw new IllegalArgumentException("第 " + edit.index() + " 块文本超过 "
                        + MAX_EDIT_TEXT_CHARS + " 字符上限");
            }
            DocBlock old = blocks.get(edit.index());
            if (old instanceof DocBlock.Paragraph) {
                blocks.set(edit.index(), new DocBlock.Paragraph(List.of(DocSpan.plain(text))));
            } else if (old instanceof DocBlock.Heading h) {
                blocks.set(edit.index(), new DocBlock.Heading(h.level(), text));
            } else if (old instanceof DocBlock.ListItem li) {
                blocks.set(edit.index(), new DocBlock.ListItem(li.ordered(), li.level(),
                        List.of(DocSpan.plain(text))));
            } else if (old instanceof DocBlock.Quote) {
                blocks.set(edit.index(), new DocBlock.Quote(List.of(DocSpan.plain(text))));
            } else if (old instanceof DocBlock.CodeBlock c) {
                blocks.set(edit.index(), new DocBlock.CodeBlock(c.language(), text));
            } else {
                throw new IllegalArgumentException("第 " + edit.index()
                        + " 块是 " + old.getClass().getSimpleName() + "，暂不支持编辑");
            }
            applied++;
        }
        session.touch();
        return applied;
    }

    /** 导出：先套用修改，再按格式序列化。 */
    public byte[] export(Session session, List<Edit> edits, String fmt) {
        apply(session, edits);
        String f = fmt == null ? "" : fmt.toLowerCase();
        String text = switch (f) {
            case "md", "markdown" -> DocConverter.toMarkdown(session.doc());
            case "txt", "plain", "text" -> DocConverter.toPlainText(session.doc());
            case "html" -> DocConverter.toHtml(session.doc());
            default -> throw new IllegalArgumentException("不支持的导出格式: " + fmt);
        };
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 按导出格式给出带扩展名的文件名。 */
    public static String exportName(Session session, String fmt) {
        String f = fmt == null ? "" : fmt.toLowerCase();
        String ext = switch (f) {
            case "md", "markdown" -> "md";
            case "txt", "plain", "text" -> "txt";
            case "html" -> "html";
            default -> "bin";
        };
        String base = MillBatch.stripExtension(session.name());
        return (base.isEmpty() ? "file" : base) + "." + ext;
    }
}
