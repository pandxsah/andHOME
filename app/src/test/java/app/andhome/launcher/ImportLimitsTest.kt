package app.andhome.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportLimitsTest {
    @Test fun importReadsTheCompleteAllowedPayload() {
        val bytes = "portable layout".toByteArray()
        assertArrayEquals(bytes, readBounded(ByteArrayInputStream(bytes), bytes.size))
    }
    @Test(expected = IllegalArgumentException::class)
    fun oversizedImportIsRejectedBeforeParsing() { readBounded(ByteArrayInputStream(ByteArray(4096)), 256) }
}
