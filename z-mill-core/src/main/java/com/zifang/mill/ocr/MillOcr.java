package com.zifang.mill.ocr;

/**
 * 扫描件 OCR 适配器 SPI：对单张图片转写，返回纯文本；null/空 = 未识别。
 *
 * <p>实现方由宿主注入（web/CLI 都走 {@link MillOcrs#fromConfig}）。默认不启用任何
 * OCR（{@link NoopMillOcr}），保证 z-mill 缺配置时行为与接入前一致。
 * 转写失败必须返回 null，不允许抛异常中断研磨。</p>
 */
public interface MillOcr {

    /**
     * @param image    图片字节（PNG/JPEG）
     * @param mimeType 图片 MIME，可能为 null
     * @return 纯文本转写；未识别/失败返回 null
     */
    String transcribe(byte[] image, String mimeType);
}