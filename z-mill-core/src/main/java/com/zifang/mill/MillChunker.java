package com.zifang.mill;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocSpan;

import java.util.ArrayList;
import java.util.List;

/** 标题驱动的分块：标题开新块并保留标题路径，超限在块边界续切，绝不切碎一个段落或一行表格。 */
public final class MillChunker {

    /** 一块资料：headingPath 是各级标题文本（缺级为空串），heading 是最近的标题行，text 是自足的 Markdown 正文。 */
    public record Chunk(List<String> headingPath, String heading, String text) {
    }

    private static final int DEFAULT_MAX_CHARS = 1200;

    private MillChunker() {
    }

    public static List<Chunk> chunk(DocDocument doc) {
        return chunk(doc, DEFAULT_MAX_CHARS);
    }

    public static List<Chunk> chunk(DocDocument doc, int maxChars) {
        List<Chunk> chunks = new ArrayList<>();
        List<String> path = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String heading = "";
        for (DocBlock block : doc.getBlocks()) {
            if (block instanceof DocBlock.Heading h) {
                flush(chunks, path, heading, buffer);
                String text = spansText(h.spans());
                while (path.size() >= h.level()) {
                    path.remove(path.size() - 1);
                }
                while (path.size() < h.level() - 1) {
                    path.add("");
                }
                path.add(text);
                heading = text;
                buffer.append("#".repeat(Math.min(h.level(), 6))).append(' ').append(text).append('\n');
                continue;
            }
            appendBlockLines(buffer, block);
            if (buffer.length() >= maxChars) {
                flush(chunks, path, heading, buffer);
            }
        }
        flush(chunks, path, heading, buffer);
        return chunks;
    }

    private static void flush(List<Chunk> chunks, List<String> path, String heading, StringBuilder buffer) {
        String text = buffer.toString().strip();
        buffer.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        chunks.add(new Chunk(List.copyOf(path), heading, text));
    }

    private static void appendBlockLines(StringBuilder buffer, DocBlock block) {
        if (block instanceof DocBlock.Paragraph p) {
            buffer.append(spansText(p.spans())).append('\n');
        } else if (block instanceof DocBlock.ListItem li) {
            buffer.append(li.ordered() ? "1. " : "- ")
                    .append(spansText(li.spans())).append('\n');
        } else if (block instanceof DocBlock.Quote q) {
            buffer.append("> ").append(spansText(q.spans())).append('\n');
        } else if (block instanceof DocBlock.CodeBlock c) {
            buffer.append("```").append(c.language() == null ? "" : c.language()).append('\n')
                    .append(c.text()).append('\n').append("```\n");
        } else if (block instanceof DocBlock.Table t) {
            for (DocBlock.Table.Row row : t.rows()) {
                appendPipeRow(buffer, row.cells().stream().map(MillChunker::cellText).toList());
            }
        } else if (block instanceof DocBlock.Sheet s) {
            buffer.append("### ").append(s.name()).append('\n');
            for (DocBlock.Sheet.SheetRow row : s.rows()) {
                if (row.cells().isEmpty()) {
                    continue;
                }
                appendPipeRow(buffer, row.cells().stream().map(DocBlock.Sheet.SheetCell::text).toList());
            }
        } else if (block instanceof DocBlock.Slides sl) {
            for (DocBlock.Slides.Slide slide : sl.slides()) {
                buffer.append("## ").append(slide.title() == null ? "Slide " + slide.index() : slide.title())
                        .append('\n');
                for (DocBlock inner : slide.blocks()) {
                    appendBlockLines(buffer, inner);
                }
            }
        } else if (block instanceof DocBlock.Image img) {
            buffer.append('[').append(img.name() == null || img.name().isBlank() ? "图片" : img.name()).append("]\n");
        }
    }

    private static void appendPipeRow(StringBuilder buffer, List<String> cells) {
        buffer.append('|');
        for (String cell : cells) {
            buffer.append(' ').append(cell).append(" |");
        }
        buffer.append('\n');
    }

    private static String cellText(DocBlock.Table.Cell cell) {
        StringBuilder sb = new StringBuilder();
        for (DocBlock block : cell.blocks()) {
            if (block instanceof DocBlock.Paragraph p) {
                sb.append(spansText(p.spans()));
            } else if (block instanceof DocBlock.Heading h) {
                sb.append(spansText(h.spans()));
            } else if (block instanceof DocBlock.CodeBlock c) {
                sb.append(c.text());
            } else if (block instanceof DocBlock.Table nested) {
                for (DocBlock.Table.Row row : nested.rows()) {
                    appendPipeRow(sb, row.cells().stream().map(MillChunker::cellText).toList());
                }
            }
        }
        return sb.toString().replace("|", "\\|").strip();
    }

    private static String spansText(List<DocSpan> spans) {
        StringBuilder sb = new StringBuilder();
        for (DocSpan span : spans) {
            sb.append(span.text());
        }
        return sb.toString();
    }
}
