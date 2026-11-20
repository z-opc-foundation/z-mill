package com.zifang.mill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MillBatchTest {

    @TempDir
    Path temp;

    @Test
    void batchGrindsDirectoryAndMirrorsLayout() throws IOException {
        Path input = temp.resolve("docs");
        Files.createDirectories(input.resolve("sub"));
        Files.writeString(input.resolve("hello.txt"), "hello mill\n\nsecond paragraph", StandardCharsets.UTF_8);
        Files.writeString(input.resolve("sub").resolve("nested.txt"), "嵌套文件", StandardCharsets.UTF_8);
        // 二进制垃圾：嗅探不出格式 → unsupported
        Files.write(input.resolve("junk.bin"), new byte[]{0x00, 0x01, (byte) 0xFF, 0x00, 0x01, (byte) 0xFF});

        Mill mill = new Mill();
        Path out = temp.resolve("out");
        MillBatch.Report report = MillBatch.grindAll(mill, input, out,
                List.of(MillView.MARKDOWN, MillView.JSON));

        assertEquals(3, report.total());
        assertEquals(2, report.ok());
        assertEquals(1, report.unsupported());
        assertEquals(0, report.failed());

        assertTrue(Files.readString(out.resolve("hello.md")).contains("hello mill"));
        assertTrue(Files.readString(out.resolve("hello.json")).startsWith("{\"meta\":{"));
        assertTrue(Files.readString(out.resolve("sub").resolve("nested.md")).contains("嵌套文件"));
        assertTrue(Files.notExists(out.resolve("junk.md")));
        assertTrue(report.details().stream().anyMatch(d -> d.startsWith("unsupported junk.bin")), report.details().toString());
    }

    @Test
    void stripExtensionKeepsDottedFilesIntact() {
        assertEquals("hello", MillBatch.stripExtension("hello.txt"));
        assertEquals("archive.tar", MillBatch.stripExtension("archive.tar.gz"));
        assertEquals(".hidden", MillBatch.stripExtension(".hidden"));
        assertEquals("noext", MillBatch.stripExtension("noext"));
    }
}
