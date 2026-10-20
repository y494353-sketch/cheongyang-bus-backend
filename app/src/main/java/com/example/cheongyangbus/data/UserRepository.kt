package com.example.cheongyangbus.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 회원가입·로그인. 화면(Activity)은 이 클래스만 부르면 됩니다. */
class UserRepository(context: Context) {

    private val dao = AppDatabase.get(context).userDao()

    /** 회원가입. 성공하면 true, 이미 있는 아이디면 false */
    suspend fun signup(loginId: String, password: String, nickname: String): Boolean {
        val hash = withContext(Dispatchers.Default) { hashPassword(password) }
        return try {
            dao.insert(User(loginId = loginId, passwordHash = hash, nickname = nickname))
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    /** 로그인. 아이디와 비밀번호가 맞으면 그 회원, 아니면 null */
    suspend fun login(loginId: String, password: String): User? {
        val user = dao.findByLoginId(loginId) ?: return null
        val ok = withContext(Dispatchers.Default) { checkPassword(password, user.passwordHash) }
        return if (ok) user else null
    }

    // ---- 비밀번호 해시 (PBKDF2). 저장 형식: "salt:hash" (16진수) ----

    private fun hashPassword(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return salt.toHex() + ":" + pbkdf2(password, salt).toHex()
    }

    private fun checkPassword(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return MessageDigest.isEqual(pbkdf2(password, parts[0].hexToBytes()), parts[1].hexToBytes())
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
