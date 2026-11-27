package com.zifang.mill;

import com.zifang.mill.ocr.MillOcr;
import com.zifang.mill.ocr.NoopMillOcr;
import com.zifang.mill.ocr.OcrEnricher;
import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocFormat;
import com.zifang.util.doc.DocReaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 磨坊门面：嗅探 → 读取 → OCR 补全 → 渲染，一条龙。资料进、成品出。 */
public final class Mill {

    private final DocReaders readers;
    private final OcrEnricher enricher;

    /** 用 classpath 上全部 DocReader（META-INF/services）组装磨坊。 */
    public Mill() {
        this(DocReaders.discover(), NoopMillOcr.INSTANCE);
    }

    public Mill(DocReaders readers) {
        this(readers, NoopMillOcr.INSTANCE);
    }

    /** 带 OCR 适配器的磨坊；适配器为 null 视为不启用。 */
    public Mill(MillOcr ocr) {
        this(DocReaders.discover(), ocr);
    }

    public Mill(DocReaders readers, MillOcr ocr) {
        this.readers = readers;
        this.enricher = new OcrEnricher(ocr);
    }

    public DocFormat detect(byte[] data) {
        return readers.detect(data);
    }

    public boolean supports(byte[] data) {
        return readers.supports(data);
    }

    public MillOutput grind(byte[] data, String name) throws IOException {
        DocFormat format = readers.detect(data);
        DocDocument document = readers.read(data);
        enricher.enrich(format, document);
        return new MillOutput(name, format, document);
    }

    public MillOutput grind(Path file) throws IOException {
        return grind(Files.readAllBytes(file), file.getFileName().toString());
    }
}
