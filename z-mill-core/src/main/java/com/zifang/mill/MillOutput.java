package com.zifang.mill;

import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocFormat;

/** 一次研磨的产出：文档模型 + 各渲染形态按需计算。 */
public record MillOutput(String name, DocFormat format, DocDocument document) {

    public String markdown() {
        return document.toMarkdown();
    }

    public String plainText() {
        return document.toPlainText();
    }

    public String html() {
        return document.toHtml();
    }

    public String json() {
        return MillJson.toJson(document);
    }
}
