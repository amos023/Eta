package io.github.mangi.eta.agent.connector

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** 授权过程中需要告诉用户的那部分信息。 */
internal data class DeviceAuthorization(
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
    val deviceCode: String,
)

internal class GitHubAuthException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * GitHub Device Flow 客户端。
 *
 * 内置一个公开的 OAuth App Client ID，用户不需要自己建应用、也不需要填任何 ID：
 * 点一下「连接 GitHub」就得到 8 位码，去浏览器确认即可。
 * Client ID 是 OAuth 的公开标识，不是密钥；令牌仍然只存在本机 Keystore 里。
 */
internal object GitHubDeviceFlow {
    /**
     * GitHub CLI 的公开 OAuth App Client ID。
     *
     * Device Flow 只需要 Client ID，不需要 Client Secret，因此可以在客户端硬编码。
     * 授权记录会出现在 GitHub「Settings → Applications → Authorized OAuth Apps」，
     * 用户可随时单方面撤销。
     */
    const val CLIENT_ID = "178c6fc778ccc68e1d6a"

    /**
     * 与参考实现一致的权限集合：读写仓库、读组织、workflow、gist。
     *
     * 之所以不再收窄成只读：连上之后要走官方远程 MCP 做写操作
     * （提 issue、开 PR、合并），scope 不够那边会全部 403。
     */
    const val SCOPE = "repo read:org workflow gist read:user"

    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val USER_AGENT = "Eta-Android"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 申请一次性授权码；不需要任何用户输入。 */
    fun requestDeviceCode(): DeviceAuthorization {
        val body = form(
            "client_id" to CLIENT_ID,
            "scope" to SCOPE,
        )
        val json = post(DEVICE_CODE_URL, body)
        val userCode = json.optString("user_code")
        val deviceCode = json.optString("device_code")
        if (userCode.isBlank() || deviceCode.isBlank()) {
            throw GitHubAuthException(
                json.optString("error", "GITHUB_DEVICE_CODE_FAILED"),
                json.optString("error_description", "GitHub 未返回设备码"),
            )
        }
        return DeviceAuthorization(
            userCode = userCode,
            verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
            expiresInSeconds = json.optInt("expires_in", 900),
            intervalSeconds = json.optInt("interval", 5).coerceAtLeast(5),
            deviceCode = deviceCode,
        )
    }

    /** 单次轮询；返回 null 表示用户还没确认，需要继续等。 */
    fun pollOnce(deviceCode: String): String? {
        val json = post(
            TOKEN_URL,
            form(
                "client_id" to CLIENT_ID,
                "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
        )
        val token = json.optString("access_token")
        if (token.isNotBlank()) return token
        return when (val error = json.optString("error")) {
            "authorization_pending" -> null
            "slow_down" -> null
            "expired_token" -> throw GitHubAuthException(
                "CONNECTOR_DEVICE_CODE_EXPIRED",
                "授权码已过期，请重新发起连接",
            )
            "access_denied" -> throw GitHubAuthException(
                "CONNECTOR_AUTH_DENIED",
                "授权已被取消",
            )
            "device_flow_disabled" -> throw GitHubAuthException(
                "CONNECTOR_DEVICE_FLOW_DISABLED",
                "该 OAuth App 未启用 Device Flow",
            )
            "incorrect_client_credentials" -> throw GitHubAuthException(
                "CONNECTOR_CLIENT_ID_INVALID",
                "内置 Client ID 无效，请更新 App",
            )
            "" -> throw GitHubAuthException("CONNECTOR_AUTH_FAILED", "GitHub 未返回访问令牌")
            else -> throw GitHubAuthException(
                "CONNECTOR_AUTH_FAILED",
                json.optString("error_description", "GitHub 授权失败（$error）"),
            )
        }
    }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> "$key=${value.encodeUrl()}" }

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    private fun post(url: String, body: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    // GitHub 在 4xx 时会在响应体里给出 error / error_description，
                    // 直接翻出来，否则用户只看得到 HTTP 404 这种无从下手的噪声。
                    val payload = runCatching { JSONObject(text) }.getOrNull()
                    val error = payload?.optString("error").orEmpty()
                    val description = payload?.optString("error_description").orEmpty()
                    val detail = when {
                        description.isNotBlank() && error.isNotBlank() -> "$description（$error）"
                        description.isNotBlank() -> description
                        error.isNotBlank() -> error
                        else -> ""
                    }
                    throw GitHubAuthException(
                        code = when {
                            error.isNotBlank() ->
                                "CONNECTOR_AUTH_${error.uppercase().replace('-', '_')}"
                            else -> "CONNECTOR_AUTH_HTTP_${response.code}"
                        },
                        message = when {
                            detail.isNotBlank() -> "GitHub 授权失败：$detail"
                            else -> "GitHub 授权接口返回 HTTP ${response.code}"
                        },
                    )
                }
                runCatching { JSONObject(text) }.getOrElse {
                    throw GitHubAuthException("CONNECTOR_AUTH_BAD_RESPONSE", "GitHub 返回内容无法解析")
                }
            }
        } catch (failure: GitHubAuthException) {
            throw failure
        } catch (failure: IOException) {
            throw GitHubAuthException("CONNECTOR_NETWORK_ERROR", "无法连接 GitHub，请检查网络", failure)
        }
    }
}
