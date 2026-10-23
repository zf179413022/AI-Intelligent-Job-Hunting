package com.aijob.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FileContentHasherTest {

    @Test
    void sameContentProducesSameHash() {
        byte[] content = "hello knowledge".getBytes();
        assertEquals(FileContentHasher.sha256Hex(content), FileContentHasher.sha256Hex(content));
    }

    @Test
    void differentContentProducesDifferentHash() {
        assertNotEquals(
                FileContentHasher.sha256Hex("a".getBytes()),
                FileContentHasher.sha256Hex("b".getBytes())
        );
    }

    @Test
    void knownVectorLength() {
        assertEquals(64, FileContentHasher.sha256Hex(new byte[0]).length());
    }
}
