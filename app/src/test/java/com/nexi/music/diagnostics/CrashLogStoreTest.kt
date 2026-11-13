package com.nexi.music.diagnostics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CrashLogStoreTest {
    @Test fun boundedAppendKeepsNewestBytesWithinLimit() {
        val result = CrashLogStore.boundedAppend("old log".toByteArray(), "new entry".toByteArray(), 12)
        assertEquals(12, result.size)
        assertArrayEquals("lognew entry".toByteArray(), result)
    }

    @Test fun entryIsTruncatedFromItsBeginningWhenItExceedsLimit() {
        assertArrayEquals(
            "6789".toByteArray(),
            CrashLogStore.boundedAppend(byteArrayOf(1, 2), "0123456789".toByteArray(), 4),
        )
    }

    @Test fun zeroLimitReturnsNoBytes() {
        assertEquals(0, CrashLogStore.boundedAppend(byteArrayOf(1), byteArrayOf(2), 0).size)
    }

    @Test fun rejectsNegativeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            CrashLogStore.boundedAppend(byteArrayOf(1), byteArrayOf(2), -1)
        }
    }
}
