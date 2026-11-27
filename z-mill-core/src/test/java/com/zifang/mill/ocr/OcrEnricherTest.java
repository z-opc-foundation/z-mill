package com.zifang.mill.ocr;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrEnricherTest {

    private static DocDocument scanned(int imageCount, int textChars) {
        DocDocument doc = new DocDocument();
        doc.getMeta().setPageCount(imageCount);
        if (textChars > 0) {
            doc.getBlocks().add(new DocBlock.Paragraph("x".repeat(textChars)));
        }
        for (int i = 0; i < imageCount; i++) {
            doc.getBlocks().add(new DocBlock.Image("page-" + i + ".png", "image/png",
                    new byte[]{1, 2, 3}, 1200, 1700));
        }
        return doc;
    }

    private static final MillOcr TRANSCRIBE = (image, mime) -> "第 " + image[0] + " 页转写";

    @Test
    void replacesPageImagesWithTranscription() {
        OcrEnricher enricher = new OcrEnricher(TRANSCRIBE, 20);
        DocDocument doc = scanned(2, 3);
        enricher.enrich(DocFormat.PDF, doc);
        assertEquals(3, doc.getBlocks().size());
        int transcriptionCount = 0;
        for (DocBlock block : doc.getBlocks()) {
            if (block instanceof DocBlock.Paragraph p) {
                if (p.spans().get(0).text().contains("页转写")) {
                    transcriptionCount++;
                }
            }
        }
        assertEquals(2, transcriptionCount, "两张页图都应被转写段落替换");
    }

    @Test
    void textRichPdfUntouched() {
        OcrEnricher enricher = new OcrEnricher(TRANSCRIBE, 20);
        DocDocument doc = scanned(1, 500);
        enricher.enrich(DocFormat.PDF, doc);
        assertEquals(2, doc.getBlocks().size());
        assertTrue(doc.getBlocks().get(1) instanceof DocBlock.Image);
    }

    @Test
    void nonPdfUntouchedEvenIfImageOnly() {
        OcrEnricher enricher = new OcrEnricher(TRANSCRIBE, 20);
        DocDocument doc = scanned(1, 0);
        enricher.enrich(DocFormat.DOCX, doc);
        assertEquals(1, doc.getBlocks().size());
        assertTrue(doc.getBlocks().get(0) instanceof DocBlock.Image);
    }

    @Test
    void nullOcrKeepsImages() {
        OcrEnricher enricher = new OcrEnricher(null, 20);
        DocDocument doc = scanned(1, 0);
        enricher.enrich(DocFormat.PDF, doc);
        assertEquals(1, doc.getBlocks().size());
        assertTrue(doc.getBlocks().get(0) instanceof DocBlock.Image);
    }

    @Test
    void ocrFailureKeepsImage() {
        OcrEnricher enricher = new OcrEnricher((image, mime) -> null, 20);
        DocDocument doc = scanned(1, 0);
        enricher.enrich(DocFormat.PDF, doc);
        assertEquals(1, doc.getBlocks().size());
        assertTrue(doc.getBlocks().get(0) instanceof DocBlock.Image);
    }

    @Test
    void ocrThrowingKeepsImage() {
        OcrEnricher enricher = new OcrEnricher((image, mime) -> {
            throw new IllegalStateException("upstream down");
        }, 20);
        DocDocument doc = scanned(1, 0);
        enricher.enrich(DocFormat.PDF, doc);
        assertEquals(1, doc.getBlocks().size());
        assertTrue(doc.getBlocks().get(0) instanceof DocBlock.Image);
    }

    @Test
    void blankTranscriptionKeepsImage() {
        OcrEnricher enricher = new OcrEnricher((image, mime) -> "   \n  ", 20);
        DocDocument doc = scanned(1, 0);
        enricher.enrich(DocFormat.PDF, doc);
        assertEquals(1, doc.getBlocks().size());
        assertTrue(doc.getBlocks().get(0) instanceof DocBlock.Image);
    }

    @Test
    void textOfCountsAllBlockKinds() {
        DocDocument doc = new DocDocument();
        doc.getBlocks().add(new DocBlock.Heading(1, "标题"));
        doc.getBlocks().add(new DocBlock.CodeBlock("java", "code here"));
        doc.getBlocks().add(new DocBlock.Quote(List.of(
                com.zifang.util.doc.DocSpan.plain("引用"))));
        int total = 0;
        for (DocBlock block : doc.getBlocks()) {
            total += OcrEnricher.textOf(block);
        }
        assertEquals(2 + 9 + 2, total);
    }
}