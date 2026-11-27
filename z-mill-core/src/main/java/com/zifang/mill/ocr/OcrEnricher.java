package com.zifang.mill.ocr;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocFormat;
import com.zifang.util.doc.DocSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 扫描件补 OCR：PDF 全文文本极少但带内嵌图 → 判为扫描件，逐图转写并替换成段落。
 *
 * <p>判据（诚实降级）：格式是 PDF、页数 ≥ 1、全文可见字符数 &lt; 每页阈值 × 页数、
 * 且存在带字节的图片块。宽度/高度在 z-util-doc-pdf 里是 null，无法按尺寸筛，
 * 扫描件等价于「文本空洞 + 页图」，逐图转写即可。</p>
 *
 * <p>单图转写失败（返回 null）或空结果时保留原图片块不删；转写成功的图被纯文本段落
 * 替换（图片随块消失，不再进 images[] 输出）。</p>
 */
public final class OcrEnricher {

    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    private final MillOcr ocr;
    private final int minCharsPerPage;

    public OcrEnricher(MillOcr ocr) {
        this(ocr, 20);
    }

    public OcrEnricher(MillOcr ocr, int minCharsPerPage) {
        this.ocr = ocr == null ? NoopMillOcr.INSTANCE : ocr;
        this.minCharsPerPage = Math.max(1, minCharsPerPage);
    }

    /** 原地丰富文档块；非扫描件或未配置 OCR 时不做任何修改。 */
    public DocDocument enrich(DocFormat format, DocDocument document) {
        if (document == null || format != DocFormat.PDF || ocr instanceof NoopMillOcr) {
            return document;
        }
        List<DocBlock> blocks = document.getBlocks();
        if (blocks.isEmpty() || !isScannedLike(document)) {
            return document;
        }
        for (int i = 0; i < blocks.size(); i++) {
            if (!(blocks.get(i) instanceof DocBlock.Image image)
                    || image.data() == null || image.data().length == 0) {
                continue;
            }
            String text = safeTranscribe(image);
            if (text == null || text.isBlank()) {
                continue;
            }
            List<DocBlock> paragraphs = paragraphsOf(text);
            blocks.remove(i);
            blocks.addAll(i, paragraphs);
            i += Math.max(0, paragraphs.size() - 1);
        }
        return document;
    }

    private boolean isScannedLike(DocDocument document) {
        List<DocBlock> blocks = document.getBlocks();
        boolean hasImage = false;
        int chars = 0;
        for (DocBlock block : blocks) {
            if (block instanceof DocBlock.Image image) {
                if (image.data() != null && image.data().length > 0) {
                    hasImage = true;
                }
            } else {
                chars += textOf(block);
            }
        }
        if (!hasImage) {
            return false;
        }
        int pages = document.getMeta().getPageCount() == null
                ? 1 : Math.max(1, document.getMeta().getPageCount());
        return chars < minCharsPerPage * pages;
    }

    private String safeTranscribe(DocBlock.Image image) {
        try {
            return ocr.transcribe(image.data(), image.mimeType());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<DocBlock> paragraphsOf(String text) {
        List<DocBlock> out = new ArrayList<>();
        for (String para : BLANK_LINE.split(text)) {
            String trimmed = para.strip();
            if (!trimmed.isEmpty()) {
                out.add(new DocBlock.Paragraph(List.of(DocSpan.plain(trimmed))));
            }
        }
        if (out.isEmpty()) {
            out.add(new DocBlock.Paragraph(List.of(DocSpan.plain(text.strip()))));
        }
        return out;
    }

    /** 统计一个块的全部可见字符（用于扫描件判定）。 */
    static int textOf(DocBlock block) {
        if (block instanceof DocBlock.Paragraph p) {
            return spansLength(p.spans());
        }
        if (block instanceof DocBlock.Heading h) {
            return spansLength(h.spans());
        }
        if (block instanceof DocBlock.ListItem li) {
            return spansLength(li.spans());
        }
        if (block instanceof DocBlock.Quote q) {
            return spansLength(q.spans());
        }
        if (block instanceof DocBlock.CodeBlock code) {
            return code.text() == null ? 0 : code.text().length();
        }
        if (block instanceof DocBlock.Table table) {
            int sum = 0;
            for (DocBlock.Table.Row row : table.rows()) {
                for (DocBlock.Table.Cell cell : row.cells()) {
                    for (DocBlock b : cell.blocks()) {
                        sum += textOf(b);
                    }
                }
            }
            return sum;
        }
        if (block instanceof DocBlock.Sheet sheet) {
            int sum = 0;
            for (DocBlock.Sheet.SheetRow row : sheet.rows()) {
                for (DocBlock.Sheet.SheetCell cell : row.cells()) {
                    sum += cell.text() == null ? 0 : cell.text().length();
                }
            }
            return sum;
        }
        if (block instanceof DocBlock.Slides slides) {
            int sum = 0;
            for (DocBlock.Slides.Slide slide : slides.slides()) {
                for (DocBlock b : slide.blocks()) {
                    sum += textOf(b);
                }
            }
            return sum;
        }
        return 0;
    }

    private static int spansLength(List<DocSpan> spans) {
        int sum = 0;
        for (DocSpan span : spans) {
            sum += span.text() == null ? 0 : span.text().length();
        }
        return sum;
    }
}