package ouija.app.core.utils

import java.security.SecureRandom

object CodeGenerator {
    private const val ALLOWED_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ"
    private val random = SecureRandom()

    fun generateCode(length: Int = 5): String {
        return (1..length)
            .map { ALLOWED_CHARS[random.nextInt(ALLOWED_CHARS.length)] }
            .joinToString("")
    }
}
