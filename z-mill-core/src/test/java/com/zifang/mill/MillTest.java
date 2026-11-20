package com.zifang.mill;

import com.zifang.util.doc.DocFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MillTest {

    private final Mill mill = new Mill();

    @Test
    void grindsPlainTextToMarkdown() throws IOException {
        byte[] data = "hello mill\n\nsecond paragraph".getBytes(StandardCharsets.UTF_8);
        assertTrue(mill.supports(data));
        assertEquals(DocFormat.TXT, mill.detect(data));

        MillOutput output = mill.grind(data, "hello.txt");
        assertEquals("hello.txt", output.name());
        assertEquals(DocFormat.TXT, output.format());
        assertTrue(output.markdown().contains("hello mill"), output.markdown());
        assertTrue(output.plainText().contains("second paragraph"));
        assertFalse(output.html().isBlank());
        assertTrue(output.json().startsWith("{\"meta\":{"), output.json());
    }

    @Test
    void rejectsBinaryGarbage() {
        byte[] data = {0x00, 0x01, 0x02, (byte) 0xFF, 0x7F, 0x00, 0x01};
        assertFalse(mill.supports(data));
        assertEquals(null, mill.detect(data));
        assertNotNull(mill);
    }
}
