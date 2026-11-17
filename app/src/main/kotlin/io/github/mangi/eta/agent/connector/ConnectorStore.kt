package io.github.mangi.eta.agent.connector

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * 连接器的状态与令牌存储。
 *
 * 状态（账号名、头像、授权范围、Client ID）用普通 App 私有偏好；
 * 访问令牌单独用 Android Keystore 派生的 AES-256-GCM 加密后再存，
 * 不进日志、不进聊天记录、不进模型请求。
 */
internal class ConnectorStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun connection(id: String): ConnectorConnection {
        val raw = runCatching { preferences.getString(stateKey(id), null) }.getOrNull()
        val json = raw?.takeIf { it.isNotBlank() }?.let { text ->
            runCatching { JSONObject(text) }.getOrNull()
        }
        return ConnectorConnection.from(id, json)
    }

    fun connections(): List<ConnectorConnection> =
        ConnectorRegistry.definitions.map { connection(it.id) }

    fun save(connection: ConnectorConnection) {
        check(
            preferences.edit()
                .putString(stateKey(connection.connectorId), connection.toJson().toString())
                .commit()
        ) { "连接器状态保存失败" }
    }

    fun disconnect(id: String) {
        val current = connection(id)
        SecretCipher(appContext).clear(id)
        save(
            current.copy(
                connected = false,
                account = "",
                avatarUrl = "",
                scopes = emptyList(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun token(id: String): String? = SecretCipher(appContext).read(id)

    fun saveToken(id: String, token: String) {
        SecretCipher(appContext).write(id, token)
    }

    private fun stateKey(id: String): String = "state_$id"

    private companion object {
        const val PREFERENCES_NAME = "eta_connector_state"
    }
}

/** Keystore 加密的凭据存取，只在本机可解密。 */
internal class SecretCipher(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun read(id: String): String? {
        val encoded = runCatching { preferences.getString(tokenKey(id), null) }.getOrNull() ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, payload, 0, GCM_IV_BYTES),
            )
            cipher.doFinal(payload, GCM_IV_BYTES, payload.size - GCM_IV_BYTES).toString(Charsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    @Synchronized
    fun write(id: String, token: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) {
            clear(id)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        check(
            preferences.edit()
                .putString(tokenKey(id), Base64.encodeToString(payload, Base64.NO_WRAP))
                .commit()
        ) { "连接器凭据保存失败" }
    }

    @Synchronized
    fun clear(id: String) {
        check(preferences.edit().remove(tokenKey(id)).commit()) { "连接器凭据删除失败" }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun tokenKey(id: String): String = "token_$id"

    private companion object {
        const val PREFERENCES_NAME = "eta_connector_secrets"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "eta_connector_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
