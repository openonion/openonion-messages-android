package ai.openonion.messages.network

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PairingLink(val recipient: String, val token: String) {
    companion object {
        fun parse(value: String): PairingLink {
            val uri = URI(value.trim())
            require(uri.scheme == "openonion" && uri.host == "sms" && uri.path == "/pair") {
                "Expected an openonion://sms/pair link"
            }
            val query = uri.rawQuery.orEmpty()
                .split('&')
                .filter { it.isNotBlank() }
                .associate { item ->
                    val parts = item.split('=', limit = 2)
                    decode(parts[0]) to decode(parts.getOrElse(1) { "" })
                }
            val recipient = query["recipient"].orEmpty()
            val token = query["token"].orEmpty()
            require(recipient.isNotBlank() && token.isNotBlank()) { "Pairing link is incomplete" }
            return PairingLink(recipient, token)
        }

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
