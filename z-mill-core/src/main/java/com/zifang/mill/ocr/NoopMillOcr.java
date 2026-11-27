package com.zifang.mill.ocr;

/** 未配置 OCR 时的空实现：永远不识别，文档保持原样。 */
public final class NoopMillOcr implements MillOcr {

    public static final NoopMillOcr INSTANCE = new NoopMillOcr();

    private NoopMillOcr() {
    }

    @Override
    public String transcribe(byte[] image, String mimeType) {
        return null;
    }
}