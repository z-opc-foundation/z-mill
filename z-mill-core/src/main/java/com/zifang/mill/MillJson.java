package com.zifang.mill;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocMeta;
import com.zifang.util.doc.DocSpan;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** DocDocument → JSON。零依赖手写发射器：转义遵循 RFC 8259，blocks 数组按类型分发。 */
public final class MillJson {

    private MillJson() {
    }

    public static String toJson(DocDocument doc) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendMeta(sb, doc.getMeta());
        sb.append(",\"blocks\":[");
        appendBlocks(sb, doc.getBlocks());
        sb.append("]}");
        return sb.toString();
    }

    private static void appendBlocks(StringBuilder sb, List<DocBlock> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendBlock(sb, blocks.get(i));
        }
    }

    private static void appendBlock(StringBuilder sb, DocBlock block) {
        if (block instanceof DocBlock.Paragraph p) {
            sb.append("{\"type\":\"paragraph\",\"spans\":");
            appendSpans(sb, p.spans());
            sb.append('}');
        } else if (block instanceof DocBlock.Heading h) {
            sb.append("{\"type\":\"heading\",\"level\":").append(h.level())
                    .append(",\"spans\":");
            appendSpans(sb, h.spans());
            sb.append('}');
        } else if (block instanceof DocBlock.ListItem li) {
            sb.append("{\"type\":\"list-item\",\"ordered\":").append(li.ordered())
                    .append(",\"level\":").append(li.level())
                    .append(",\"spans\":");
            appendSpans(sb, li.spans());
            sb.append('}');
        } else if (block instanceof DocBlock.Quote q) {
            sb.append("{\"type\":\"quote\",\"spans\":");
            appendSpans(sb, q.spans());
            sb.append('}');
        } else if (block instanceof DocBlock.CodeBlock c) {
            sb.append("{\"type\":\"code\",\"language\":");
            appendString(sb, c.language());
            sb.append(",\"text\":");
            appendString(sb, c.text());
            sb.append('}');
        } else if (block instanceof DocBlock.Image img) {
            sb.append("{\"type\":\"image\",\"name\":");
            appendString(sb, img.name());
            sb.append(",\"mimeType\":");
            appendString(sb, img.mimeType());
            sb.append(",\"bytes\":").append(img.data() == null ? 0 : img.data().length);
            if (img.width() != null) {
                sb.append(",\"width\":").append(img.width());
            }
            if (img.height() != null) {
                sb.append(",\"height\":").append(img.height());
            }
            sb.append('}');
        } else if (block instanceof DocBlock.Table t) {
            sb.append("{\"type\":\"table\",\"rows\":[");
            for (int i = 0; i < t.rows().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                DocBlock.Table.Row row = t.rows().get(i);
                sb.append("{\"header\":").append(row.header()).append(",\"cells\":[");
                for (int j = 0; j < row.cells().size(); j++) {
                    if (j > 0) {
                        sb.append(',');
                    }
                    DocBlock.Table.Cell cell = row.cells().get(j);
                    sb.append("{\"colspan\":").append(cell.colspan())
                            .append(",\"rowspan\":").append(cell.rowspan())
                            .append(",\"blocks\":");
                    appendBlocks(sb, cell.blocks());
                    sb.append('}');
                }
                sb.append("]}");
            }
            sb.append("]}");
        } else if (block instanceof DocBlock.Sheet s) {
            sb.append("{\"type\":\"sheet\",\"name\":");
            appendString(sb, s.name());
            sb.append(",\"hidden\":").append(s.hidden()).append(",\"rows\":[");
            for (int i = 0; i < s.rows().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                DocBlock.Sheet.SheetRow row = s.rows().get(i);
                sb.append("{\"index\":").append(row.index()).append(",\"cells\":[");
                for (int j = 0; j < row.cells().size(); j++) {
                    if (j > 0) {
                        sb.append(',');
                    }
                    DocBlock.Sheet.SheetCell cell = row.cells().get(j);
                    sb.append("{\"column\":").append(cell.column())
                            .append(",\"type\":");
                    appendString(sb, cell.type().name());
                    sb.append(",\"text\":");
                    appendString(sb, cell.text());
                    sb.append(",\"formula\":");
                    appendString(sb, cell.formula());
                    sb.append('}');
                }
                sb.append("]}");
            }
            sb.append("]}");
        } else if (block instanceof DocBlock.Slides sl) {
            sb.append("{\"type\":\"slides\",\"slides\":[");
            for (int i = 0; i < sl.slides().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                DocBlock.Slides.Slide slide = sl.slides().get(i);
                sb.append("{\"index\":").append(slide.index())
                        .append(",\"title\":");
                appendString(sb, slide.title());
                sb.append(",\"blocks\":");
                appendBlocks(sb, slide.blocks());
                sb.append('}');
            }
            sb.append("]}");
        }
    }

    private static void appendSpans(StringBuilder sb, List<DocSpan> spans) {
        sb.append('[');
        for (int i = 0; i < spans.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            DocSpan span = spans.get(i);
            sb.append("{\"text\":");
            appendString(sb, span.text());
            if (span.bold()) {
                sb.append(",\"bold\":true");
            }
            if (span.italic()) {
                sb.append(",\"italic\":true");
            }
            if (span.mono()) {
                sb.append(",\"mono\":true");
            }
            if (span.strike()) {
                sb.append(",\"strike\":true");
            }
            if (span.link() != null) {
                sb.append(",\"link\":");
                appendString(sb, span.link());
            }
            sb.append('}');
        }
        sb.append(']');
    }

    private static void appendMeta(StringBuilder sb, DocMeta meta) {
        sb.append("\"meta\":{");
        boolean first = true;
        first = stringField(sb, first, "title", meta.getTitle());
        first = stringField(sb, first, "author", meta.getAuthor());
        first = stringField(sb, first, "subject", meta.getSubject());
        first = stringField(sb, first, "keywords", meta.getKeywords());
        first = stringField(sb, first, "creator", meta.getCreator());
        first = stringField(sb, first, "producer", meta.getProducer());
        first = dateField(sb, first, "creationDate", meta.getCreationDate());
        first = dateField(sb, first, "modDate", meta.getModDate());
        if (meta.getPageCount() != null) {
            first = separator(sb, first);
            sb.append("\"pageCount\":").append(meta.getPageCount());
        }
        Map<String, String> extra = meta.getExtra();
        if (!extra.isEmpty()) {
            first = separator(sb, first);
            sb.append("\"extra\":{");
            boolean firstExtra = true;
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                firstExtra = separator(sb, firstExtra);
                appendString(sb, entry.getKey());
                sb.append(':');
                appendString(sb, entry.getValue());
            }
            sb.append('}');
        }
        sb.append('}');
    }

    private static boolean stringField(StringBuilder sb, boolean first, String key, String value) {
        if (value == null) {
            return first;
        }
        first = separator(sb, first);
        appendString(sb, key);
        sb.append(':');
        appendString(sb, value);
        return false;
    }

    private static boolean dateField(StringBuilder sb, boolean first, String key, OffsetDateTime value) {
        if (value == null) {
            return first;
        }
        first = separator(sb, first);
        appendString(sb, key);
        sb.append(':');
        appendString(sb, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value));
        return false;
    }

    private static boolean separator(StringBuilder sb, boolean first) {
        if (!first) {
            sb.append(',');
        }
        return false;
    }

    /** 转义并带引号的 JSON 字符串字面量；null 输出 null 字面量。供本仓手写 JSON 发射器复用。 */
    public static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        appendString(sb, value);
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
