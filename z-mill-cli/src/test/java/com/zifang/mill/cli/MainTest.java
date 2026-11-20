package com.zifang.mill.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path temp;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @Test
    void grindsSingleFileNextToInput() throws Exception {
        Path input = temp.resolve("report.txt");
        Files.writeString(input, "正文一行", StandardCharsets.UTF_8);

        int code = run(input.toString());
        assertEquals(0, code, stderr.toString());
        assertTrue(Files.readString(temp.resolve("report.md")).contains("正文一行"));
    }

    @Test
    void grindsDirectoryWithViews() throws Exception {
        Path input = temp.resolve("docs");
        Files.createDirectories(input);
        Files.writeString(input.resolve("a.txt"), "甲", StandardCharsets.UTF_8);
        Path out = temp.resolve("out");

        int code = run(input.toString(), "-o", out.toString(), "-f", "md,json,txt");
        assertEquals(0, code, stderr.toString());
        assertTrue(Files.readString(out.resolve("a.md")).contains("甲"));
        assertTrue(Files.readString(out.resolve("a.json")).contains("甲"));
        assertTrue(Files.readString(out.resolve("a.txt")).contains("甲"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("成功 1"), stdout.toString());
    }

    @Test
    void usageErrorsReturnOne() {
        assertEquals(1, run());
        assertEquals(1, run(temp.resolve("no-such-file.txt").toString()));
        assertEquals(1, run(temp.toString(), "-x", "值"));
        assertTrue(stderr.toString().contains("用法"));
    }

    @Test
    void unsupportedFileReturnsTwo() throws Exception {
        Path input = temp.resolve("junk.bin");
        Files.write(input, new byte[]{0x00, 0x01, (byte) 0xFF, 0x00, 0x01, (byte) 0xFF});
        assertEquals(2, run(input.toString()));
        assertTrue(stderr.toString().contains("不支持"), stderr.toString());
    }
}
