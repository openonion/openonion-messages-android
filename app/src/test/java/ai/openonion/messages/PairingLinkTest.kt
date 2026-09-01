package ai.openonion.messages

import ai.openonion.messages.network.PairingLink
import ai.openonion.messages.protocol.SmsEncryptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PairingLinkTest {
    private val address = "0x" + "ab".repeat(32)

    @Test
    fun parsesPairingLinkWithoutChangingSecrets() {
        val link = PairingLink.parse(
            "openonion://sms/pair?recipient=$address&token=sms_pair_a%2Bb",
        )
        assertEquals(address, link.recipient)
        assertEquals("sms_pair_a+b", link.token)
    }

    @Test
    fun rejectsWebLinksAndIncompleteLinks() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingLink.parse("https://example.com/sms/pair?recipient=$address&token=x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PairingLink.parse("openonion://sms/pair?recipient=$address")
        }
    }

    @Test
    fun addressIsExactlyAnEd25519PublicKey() {
        assertEquals(32, SmsEncryptor.parseAddress(address).size)
        assertThrows(IllegalArgumentException::class.java) {
            SmsEncryptor.parseAddress("0x1234")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SmsEncryptor.parseAddress("0x" + "zz".repeat(32))
        }
    }
}
