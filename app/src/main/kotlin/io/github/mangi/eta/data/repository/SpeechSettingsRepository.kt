package io.github.mangi.eta.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal object SpeechSettingsRepository {
    private const val KEY_ALIAS = "eta_speech_credentials_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun settingsFlow(): Flow<SpeechSettings> = SettingsDataStore.speechSettingsFlow()
    suspend fun settings(): SpeechSettings = SettingsDataStore.speechSettings()
    suspend fun save(settings: SpeechSettings) = SettingsDataStore.setSpeechSettings(settings)

    suspend fun credentials(context: Context): SpeechCredentials = withContext(Dispatchers.IO) {
        val encoded = context.getSharedPreferences("eta_speech_secrets", Context.MODE_PRIVATE)
            .getString("credentials", null) ?: return@withContext SpeechCredentials()
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            json.decodeFromString<SpeechCredentials>(
                cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            // 密钥已作废或密文损坏：丢弃旧密文并返回空凭据，让用户可以重新填写后保存，
            // 而不是让整个语音设置页因为读不出旧凭据而无法保存。
            runCatching {
                context.getSharedPreferences("eta_speech_secrets", Context.MODE_PRIVATE)
                    .edit().remove("credentials").apply()
            }
            SpeechCredentials()
        }
    }

    suspend fun saveCredentials(context: Context, credentials: SpeechCredentials) = withContext(Dispatchers.IO) {
        // 完全没有凭据时（例如只用本地离线识别与系统播报）不必写加密存储：
        // 既省掉一次 Keystore 加密，也不会因为密钥失效而阻断整条保存链路。
        if (credentials.isBlank()) {
            runCatching {
                context.getSharedPreferences("eta_speech_secrets", Context.MODE_PRIVATE)
                    .edit().remove("credentials").commit()
            }
            return@withContext
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(json.encodeToString(credentials).toByteArray(Charsets.UTF_8))
        check(context.getSharedPreferences("eta_speech_secrets", Context.MODE_PRIVATE).edit()
            .putString("credentials", Base64.getEncoder().encodeToString(encrypted)).commit()) {
            "语音凭据保存失败"
        }
    }

    /**
     * 取凭据加密密钥。
     *
     * Keystore 里的条目可能因锁屏凭据变更、生物识别重新注册、系统安全策略更新等原因
     * 被系统永久作废：此时 [KeyStore.getKey] 仍能取回对象，但用它初始化 Cipher 会抛
     * KeyPermanentlyInvalidatedException。这里先实测能否初始化，失败就删掉重建，
     * 避免整条保存链路因为一个废密钥永久失败。
     */
    @Synchronized
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null && existing.isUsable()) return existing
        // 失效或不存在：清掉残留条目后重新生成。
        runCatching { store.deleteEntry(KEY_ALIAS) }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    /** 密钥是否还能用：作废的密钥能取回，但初始化 Cipher 会直接抛异常。 */
    private fun SecretKey.isUsable(): Boolean = runCatching {
        Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, this)
    }.isSuccess
}

internal class SpeechCredentialsUnavailable : Exception("语音凭据无法解密，请重新填写并保存")
