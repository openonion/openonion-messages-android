package ai.openonion.messages

import ai.openonion.messages.ui.ScanVerdict
import ai.openonion.messages.ui.decodeQr
import ai.openonion.messages.ui.judgeScan
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingScannerTest {
    // Shape copied from a real `co sms pair --json` link; values are not secret.
    private val link =
        "openonion://sms/pair?v=2&id=bf82a36b-b380-4198-a8d8-3138a3e7cd55" +
            "&recipient=0x10e68f6dff39ab1c50cc48ea1c74e7fd6ce7269aa6e8123829b344e57d005508" +
            "&nonce=nS0dveBgEqPzYGFxFfAHWiA7t8lsSUypq20mtRgnvDo&expires=1793661434" +
            "&signature=97f4221bf26b1abcd956e81365a58e5d5841473459a08abd62489e297ba2f1ec" +
            "06432e49ff6569f42295a8fd98a377f2b526e2ec8fcabb8152ab106e9c4ead04"

    @Test
    fun readsDarkOnLightCode() {
        val frame = render(link, inverted = false, padding = 0)
        assertEquals(link, decodeQr(frame.bytes, frame.rowStride, frame.width, frame.height))
    }

    @Test
    fun readsLightOnDarkCodeFromADarkTerminal() {
        val frame = render(link, inverted = true, padding = 0)
        assertEquals(link, decodeQr(frame.bytes, frame.rowStride, frame.width, frame.height))
    }

    @Test
    fun honoursCameraRowPadding() {
        val frame = render(link, inverted = true, padding = 64)
        assertEquals(link, decodeQr(frame.bytes, frame.rowStride, frame.width, frame.height))
    }

    @Test
    fun blankFrameHasNoCode() {
        assertNull(decodeQr(ByteArray(320 * 240) { 0x7f }, 320, 320, 240))
    }

    @Test
    fun judgesScannedText() {
        assertEquals(ScanVerdict.Pair(link), judgeScan(link, nowEpochSeconds = 1793661000))
        assertEquals(ScanVerdict.Expired, judgeScan(link, nowEpochSeconds = 1793661434))
        assertEquals(ScanVerdict.NotOurs, judgeScan("https://example.com/menu", nowEpochSeconds = 0))
    }

    private class Frame(val bytes: ByteArray, val rowStride: Int, val width: Int, val height: Int)

    private fun render(text: String, inverted: Boolean, padding: Int): Frame {
        val size = 600
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val rowStride = size + padding
        val bytes = ByteArray(rowStride * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dark = matrix[x, y] != inverted
                bytes[y * rowStride + x] = if (dark) 0x10 else 0xF0.toByte()
            }
        }
        return Frame(bytes, rowStride, size, size)
    }
}
