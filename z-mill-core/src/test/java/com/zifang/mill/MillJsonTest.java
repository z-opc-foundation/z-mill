package com.zifang.mill;

import com.zifang.util.doc.DocBlock;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MillJsonTest {

    @Test
    void escapesStringsPerRfc8259() {
        DocDocument doc = new DocDocument();
        doc.add(new DocBlock.Paragraph("他说\"hi\"\n第二行\t制表\\反斜杠"));
        String json = MillJson.toJson(doc);
        assertTrue(json.contains("他说\\\"hi\\\"\\n第二行\\t制表\\\\反斜杠"), json);
    }

    @Test
    void emitsAllBlockTypes() {
        DocDocument doc = new DocDocument();
        doc.add(new DocBlock.Heading(2, "标题"));
        doc.add(new DocBlock.Paragraph("正文"));
        doc.add(new DocBlock.ListItem(true, 0, List.of(DocSpan.plain("第一项"))));
        doc.add(new DocBlock.Quote(List.of(DocSpan.plain("引用"))));
        doc.add(new DocBlock.CodeBlock("java", "int x = 1;"));
        doc.add(new DocBlock.Table(List.of(new DocBlock.Table.Row(
                List.of(DocBlock.Table.Cell.of("A1")), true))));
        doc.add(new DocBlock.Sheet("Sheet1", List.of(new DocBlock.Sheet.SheetRow(1,
                List.of(DocBlock.Sheet.SheetCell.of(0, DocBlock.Sheet.CellType.STRING, "单元格")))), false));
        doc.add(new DocBlock.Slides(List.of(new DocBlock.Slides.Slide(1, "封面",
                List.of(new DocBlock.Paragraph("内容"))))));
        doc.add(new DocBlock.Image("pic", "image/png", new byte[16], null, null));

        String json = MillJson.toJson(doc);
        assertTrue(json.contains("\"type\":\"heading\",\"level\":2"), json);
        assertTrue(json.contains("\"type\":\"paragraph\""), json);
        assertTrue(json.contains("\"type\":\"list-item\",\"ordered\":true"), json);
        assertTrue(json.contains("\"type\":\"quote\""), json);
        assertTrue(json.contains("\"type\":\"code\",\"language\":\"java\""), json);
        assertTrue(json.contains("\"type\":\"table\""), json);
        assertTrue(json.contains("\"header\":true"), json);
        assertTrue(json.contains("\"type\":\"sheet\""), json);
        assertTrue(json.contains("\"type\":\"STRING\",\"text\":\"单元格\""), json);
        assertTrue(json.contains("\"type\":\"slides\""), json);
        assertTrue(json.contains("\"type\":\"image\",\"name\":\"pic\",\"mimeType\":\"image/png\",\"bytes\":16"), json);
    }

    @Test
    void emitsSpanStylesAndMeta() {
        DocDocument doc = new DocDocument();
        doc.getMeta().setTitle("磨坊手册");
        doc.getMeta().setAuthor("yuku");
        doc.getMeta().putExtra("来源", "归档");
        doc.add(new DocBlock.Paragraph(List.of(
                DocSpan.plain("普通").withBold().withItalic().withMono().withStrike()
                        .withLink("https://example.com"))));
        String json = MillJson.toJson(doc);
        assertTrue(json.contains("\"title\":\"磨坊手册\""), json);
        assertTrue(json.contains("\"author\":\"yuku\""), json);
        assertTrue(json.contains("\"extra\":{\"来源\":\"归档\"}"), json);
        assertTrue(json.contains("\"bold\":true,\"italic\":true,\"mono\":true,\"strike\":true"), json);
        assertTrue(json.contains("\"link\":\"https://example.com\""), json);
        assertFalse(json.contains("\"pageCount\""), json);
    }
}
