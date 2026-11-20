package com.zifang.mill;

import com.zifang.util.doc.DocDocument;
import com.zifang.util.doc.DocFormat;
import com.zifang.util.doc.DocReaders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 磨坊门面：嗅探 → 读取 → 渲染，一条龙。资料进、成品出。 */
public final class Mill {

    private final DocReaders readers;

    /** 用 classpath 上全部 DocReader（META-INF/services）组装磨坊。 */
    public Mill() {
        this(DocReaders.discover());
    }

    public Mill(DocReaders readers) {
        this.readers = readers;
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
        return new MillOutput(name, format, document);
    }

    public MillOutput grind(Path file) throws IOException {
        return grind(Files.readAllBytes(file), file.getFileName().toString());
    }
}
