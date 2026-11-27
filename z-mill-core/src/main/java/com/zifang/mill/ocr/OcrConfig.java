package com.zifang.mill.ocr;

/**
 * OCR 引擎配置。baseUrl 留空 = 关闭 OCR（{@link MillOcrs#fromConfig} 返回 Noop）。
 *
 * <p>引擎走 OpenAI 兼容的视觉聊天接口（image_url + dataURL），默认指向自研 AI 底座
 * z-llm-gateway —— 模型转写走自家网关，不引任何外部三方服务。</p>
 */
public record OcrConfig(String baseUrl, String model, String apiKey, int timeoutMs, String prompt) {

    public static final String DEFAULT_MODEL = "qwen-vl-max";
    public static final int DEFAULT_TIMEOUT_MS = 60_000;

    public static final String DEFAULT_PROMPT =
            "你是 OCR 引擎。请把这张扫描文档页面图片中的全部文字逐字转写成纯文本："
                    + "不要 Markdown 标记、不要代码围栏、不要任何解释；"
                    + "保留段落结构（段落之间用空行分隔）；表格保留为逐行列文本。";

    public static OcrConfig disabled() {
        return new OcrConfig(null, DEFAULT_MODEL, null, DEFAULT_TIMEOUT_MS, DEFAULT_PROMPT);
    }

    public int timeoutMillis() {
        return timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
    }
}