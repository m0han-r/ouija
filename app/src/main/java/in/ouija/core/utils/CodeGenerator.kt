package ouija.app.core.utils

import android.content.Context
import java.security.SecureRandom

object CodeGenerator {
    private const val ALLOWED_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val PREFS_NAME = "ouija_session_prefs"
    private const val KEY_ROOM_CODE = "saved_room_code"
    private const val KEY_LAST_CONTROLLER_CODE = "last_controller_code"
    private val random = SecureRandom()

    fun generateCode(length: Int = 5): String {
        return (1..length)
            .map { ALLOWED_CHARS[random.nextInt(ALLOWED_CHARS.length)] }
            .joinToString("")
    }

    fun getOrGenerateRoomCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingCode = prefs.getString(KEY_ROOM_CODE, null)
        if (!existingCode.isNullOrBlank()) {
            return existingCode
        }
        val newCode = generateCode()
        prefs.edit().putString(KEY_ROOM_CODE, newCode).apply()
        return newCode
    }

    fun regenerateRoomCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val newCode = generateCode()
        prefs.edit().putString(KEY_ROOM_CODE, newCode).apply()
        return newCode
    }

    fun getLastConnectedCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_CONTROLLER_CODE, "") ?: ""
    }

    fun saveLastConnectedCode(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_CONTROLLER_CODE, code).apply()
    }
}

