package com.zifang.mill;

import com.zifang.util.doc.DocBlock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MillEditTest {

    private final MillEdit edit = new MillEdit();

    private MillEdit.Session openSample() throws IOException {
        return edit.session(edit.open("sample.txt",
                "第一行\n第二行\n\n第三行".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void openApplyExportRoundTrip() throws IOException {
        MillEdit.Session s = openSample();
        assertEquals("sample.txt", s.name());
        int n = edit.apply(s, List.of(new MillEdit.Edit(0, "改过的第一行")));
        assertEquals(1, n);
        String md = new String(edit.export(s, List.of(), "md"), StandardCharsets.UTF_8);
        assertTrue(md.contains("改过的第一行"), md);
        assertTrue(!md.contains("第一行\n改"), md);
        String txt = new String(edit.export(s, List.of(), "txt"), StandardCharsets.UTF_8);
        assertTrue(txt.contains("改过的第一行"));
        String html = new String(edit.export(s, List.of(), "html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("改过的第一行"));
    }

    @Test
    void editKeepsHeadingLevel() throws IOException {
        MillEdit.Session s = edit.session(edit.open("doc.html",
                "<html><body><h1>大标题</h1><p>正文</p></body></html>"
                        .getBytes(StandardCharsets.UTF_8)));
        DocBlock.Heading before = (DocBlock.Heading) s.doc().getBlocks().get(0);
        assertEquals(1, before.level());
        edit.apply(s, List.of(new MillEdit.Edit(0, "新标题")));
        DocBlock.Heading after = (DocBlock.Heading) s.doc().getBlocks().get(0);
        assertEquals(1, after.level());
        assertEquals("新标题", after.spans().get(0).text());
    }

    @Test
    void rejectsOutOfBoundsAndNonTextBlocks() throws IOException {
        MillEdit.Session s = openSample();
        assertThrows(IllegalArgumentException.class,
                () -> edit.apply(s, List.of(new MillEdit.Edit(99, "越界"))));
        assertThrows(IllegalArgumentException.class,
                () -> edit.apply(s, List.of(new MillEdit.Edit(-1, "负数"))));
        MillEdit.Session s2 = edit.session(edit.open("table.html",
                ("<html><body><table><tr><td>A</td><td>B</td></tr></table>"
                        + "<p>尾段</p></body></html>").getBytes(StandardCharsets.UTF_8)));
        int tableIdx = -1;
        for (int i = 0; i < s2.doc().getBlocks().size(); i++) {
            if (s2.doc().getBlocks().get(i) instanceof DocBlock.Table) {
                tableIdx = i;
            }
        }
        assertTrue(tableIdx >= 0, "html 样张应产出表格块");
        int finalTableIdx = tableIdx;
        assertThrows(IllegalArgumentException.class,
                () -> edit.apply(s2, List.of(new MillEdit.Edit(finalTableIdx, "试图改表格"))));
    }

    @Test
    void emptyOrUnknownFileRejected() {
        assertThrows(IllegalArgumentException.class, () -> edit.open("big.bin", new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> edit.open("huge.txt", new byte[(int) (MillEdit.MAX_EDIT_FILE_BYTES + 1)]));
    }

    @Test
    void miniJsonParsesEditBody() {
        Object v = MillMiniJson.parse(
                "{\"session\":\"abc\",\"fmt\":\"md\",\"edits\":[{\"i\":0,\"text\":\"a\\\"b\\n\"}]}");
        Map<?, ?> obj = (Map<?, ?>) v;
        assertEquals("abc", obj.get("session"));
        assertEquals("md", obj.get("fmt"));
        List<?> edits = (List<?>) obj.get("edits");
        assertEquals(1, edits.size());
        Map<?, ?> e = (Map<?, ?>) edits.get(0);
        assertEquals(0.0, ((Number) e.get("i")).doubleValue());
        assertEquals("a\"b\n", e.get("text"));
    }

    @Test
    void miniJsonRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> MillMiniJson.parse("{bad"));
        assertThrows(IllegalArgumentException.class, () -> MillMiniJson.parse("[1,]x"));
    }
}
