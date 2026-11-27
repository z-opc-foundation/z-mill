package com.zifang.mill.ocr;

/** OCR 装配：配置 → 适配器；并从命令行参数解析 --ocr-* 开关。 */
public final class MillOcrs {

    private MillOcrs() {
    }

    public static MillOcr fromConfig(OcrConfig config) {
        if (config == null || config.baseUrl() == null || config.baseUrl().isBlank()) {
            return NoopMillOcr.INSTANCE;
        }
        return new LlmVisionOcr(config);
    }

    /**
     * 扫描 {@code --ocr-base-url / --ocr-model / --ocr-api-key / --ocr-timeout-ms /
     * --ocr-prompt}；未出现任何 OCR 参数时返回关闭态。
     */
    public static OcrConfig parseArgs(String[] args) {
        OcrConfig config = OcrConfig.disabled();
        String baseUrl = null;
        String model = OcrConfig.DEFAULT_MODEL;
        String apiKey = null;
        int timeoutMs = OcrConfig.DEFAULT_TIMEOUT_MS;
        String prompt = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--ocr-base-url" -> baseUrl = args[i + 1];
                case "--ocr-model" -> model = args[i + 1];
                case "--ocr-api-key" -> apiKey = args[i + 1];
                case "--ocr-timeout-ms" -> timeoutMs = Integer.parseInt(args[i + 1]);
                case "--ocr-prompt" -> prompt = args[i + 1];
                default -> {
                }
            }
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return config;
        }
        return new OcrConfig(baseUrl, model, apiKey, timeoutMs, prompt);
    }
}