package com.zifang.mill;

/** 研磨产出的渲染形态：每种形态带一个成品扩展名。 */
public enum MillView {

    MARKDOWN("md"),
    PLAIN_TEXT("txt"),
    HTML("html"),
    JSON("json");

    private final String extension;

    MillView(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public String render(MillOutput output) {
        return switch (this) {
            case MARKDOWN -> output.markdown();
            case PLAIN_TEXT -> output.plainText();
            case HTML -> output.html();
            case JSON -> output.json();
        };
    }

    /** 宽松解析：md/markdown、txt/plain/text、html、json 都认。 */
    public static MillView parse(String value) {
        return switch (value.toLowerCase()) {
            case "md", "markdown" -> MARKDOWN;
            case "txt", "plain", "text" -> PLAIN_TEXT;
            case "html" -> HTML;
            case "json" -> JSON;
            default -> throw new IllegalArgumentException("未知输出形态: " + value);
        };
    }
}
