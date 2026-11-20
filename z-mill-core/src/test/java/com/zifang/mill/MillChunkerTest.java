package com.zifang.mill;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MillChunkerTest {

    @Test
    void chunksCarryHeadingPath() {
        DocDocument doc = new DocDocument();
        doc.add(new DocBlock.Heading(1, "第一章"));
        doc.add(new DocBlock.Paragraph("开篇正文。"));
        doc.add(new DocBlock.Heading(2, "第一节"));
        doc.add(new DocBlock.Paragraph("细节正文。"));

        List<MillChunker.Chunk> chunks = MillChunker.chunk(doc, 10_000);
        assertEquals(2, chunks.size());
        assertEquals(List.of("第一章"), chunks.get(0).headingPath());
        assertEquals("第一章", chunks.get(0).heading());
        assertTrue(chunks.get(0).text().startsWith("# 第一章"), chunks.get(0).text());
        assertTrue(chunks.get(0).text().contains("开篇正文。"));
        assertEquals(List.of("第一章", "第一节"), chunks.get(1).headingPath());
        assertTrue(chunks.get(1).text().startsWith("## 第一节"));
    }

    @Test
    void headingLevelResetsPath() {
        DocDocument doc = new DocDocument();
        doc.add(new DocBlock.Heading(1, "甲"));
        doc.add(new DocBlock.Heading(2, "乙"));
        doc.add(new DocBlock.Heading(1, "丙"));
        doc.add(new DocBlock.Paragraph("正文。"));

        List<MillChunker.Chunk> chunks = MillChunker.chunk(doc, 10_000);
        assertEquals(List.of("丙"), chunks.get(2).headingPath());
    }

    @Test
    void oversizedDocumentSplitsAtBlockBoundary() {
        DocDocument doc = new DocDocument();
        doc.add(new DocBlock.Heading(1, "长文"));
        for (int i = 0; i < 10; i++) {
            doc.add(new DocBlock.Paragraph("第" + i + "段" + "内容".repeat(20)));
        }

        List<MillChunker.Chunk> chunks = MillChunker.chunk(doc, 100);
        assertTrue(chunks.size() >= 3, "size=" + chunks.size());
        for (MillChunker.Chunk chunk : chunks) {
            // 段落行必须完整：整块以换行收尾，没有半截段落
            for (String line : chunk.text().split("\n")) {
                assertTrue(line.startsWith("#") || line.endsWith("。") || line.matches("第\\d+段(内容)+"),
                        "碎行: " + line);
            }
        }
    }

    @Test
    void tablesRenderAsPipesAndSheetsKeepName() {
        DocDocument doc = new DocDocument();
        doc.add(new DocBlock.Table(List.of(
                new DocBlock.Table.Row(List.of(
                        DocBlock.Table.Cell.of("品名"), DocBlock.Table.Cell.of("数量")), true),
                new DocBlock.Table.Row(List.of(
                        DocBlock.Table.Cell.of("苹果"), DocBlock.Table.Cell.of("3")), false))));
        doc.add(new DocBlock.Sheet("Sheet1", List.of(new DocBlock.Sheet.SheetRow(1,
                List.of(DocBlock.Sheet.SheetCell.of(0, DocBlock.Sheet.CellType.STRING, "甲"),
                        DocBlock.Sheet.SheetCell.of(1, DocBlock.Sheet.CellType.NUMBER, "42")))), false));

        String text = MillChunker.chunk(doc, 10_000).get(0).text();
        assertTrue(text.contains("| 品名 | 数量 |"), text);
        assertTrue(text.contains("| 苹果 | 3 |"), text);
        assertTrue(text.contains("### Sheet1"), text);
        assertTrue(text.contains("| 甲 | 42 |"), text);
    }
}
