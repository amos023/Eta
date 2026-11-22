package io.github.mangi.eta.agent.media

import io.github.mangi.eta.agent.model.AgentHttpClient
import io.github.mangi.eta.agent.model.AgentModelFailure
import io.github.mangi.eta.agent.model.AuthorizedProviderHttp
import io.github.mangi.eta.agent.model.ProviderRequestHeaders
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.provider.ProviderConnectionProfile
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容的图像生成接口客户端。
 *
 * 只做三件事：拼 `POST {baseUrl}/images/generations`、把 `url` 与 `b64_json`
 * 两种响应都归一成字节、把非 2xx 与格式错误翻译成带具体原因的中文失败。
 *
 * 端点可以由 [Model.imageEndpoint] 覆盖，中转站路径不标准时填它即可。
 */
internal object ImageGenerationClient {

    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024L
    private const val MAX_IMAGE_BYTES = 24 * 1024 * 1024L
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }

    internal data class GeneratedImage(
        val bytes: ByteArray,
        val mimeType: String,
    )

    internal data class RequestSpec(
        val provider: ProviderSetting,
        val model: Model,
        val prompt: String,
        val size: String?,
        val count: Int,
        /** 画质档；null 或 auto 时不写入请求。 */
        val quality: String? = null,
    )

    suspend fun generate(
        request: RequestSpec,
        controller: AgentRunController = AgentRunController(),
    ): List<GeneratedImage> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { controller.cancel() }
            try {
                controller.throwIfCancelled()
                val config = request.provider
                // 显式配置的地址优先；没有配置就在候选端点里逐个试，先成功先返回。
                val explicit = request.model.imageEndpoint?.trim()?.takeIf { it.isNotBlank() }
                val endpoints = explicit?.let { listOf(it) } ?: candidateEndpoints(config)
                val endpoint = endpoints.first()
                val body = buildJsonObject {
                    put("model", request.model.modelId.trim())
                    put("prompt", request.prompt.trim())
                    put("n", request.count.coerceIn(1, 4))
                    request.size?.trim()?.takeIf { it.isNotBlank() && !it.equals("auto", true) }
                        ?.let { put("size", it) }
                    request.quality?.trim()?.takeIf { it.isNotBlank() && !it.equals("auto", true) }
                        ?.let { put("quality", it) }
                    // 明确要 base64：部分中转站不回 url，直接给字节省一次下载。
                    put("response_format", "b64_json")
                }.toString()

                val payload = try {
                    execute(
                        provider = config,
                        endpoint = endpoint,
                        body = body,
                        controller = controller,
                    )
                } catch (rejected: AgentModelFailure) {
                    // 403/400/404 常见成因：中转站不认 response_format / n / size 这类可选字段，
                    // 或路径不带 /v1。先换端点、再降级成只带 model + prompt，都失败才报错，
                    // 并把每次尝试的原始正文一起带出来。
                    val attempts = ArrayList<String>(4)
                    attempts += "$endpoint 完整参数：${rejected.message}"
                    var recovered: String? = null
                    val bodies = listOf(body, buildJsonObject {
                        put("model", request.model.modelId.trim())
                        put("prompt", request.prompt.trim())
                    }.toString())
                    loop@ for (candidate in endpoints) {
                        for ((index, candidateBody) in bodies.withIndex()) {
                            if (candidate == endpoint && index == 0) continue
                            try {
                                recovered = execute(
                                    provider = config,
                                    endpoint = candidate,
                                    body = candidateBody,
                                    controller = controller,
                                )
                                break@loop
                            } catch (failure: AgentModelFailure) {
                                attempts += "$candidate ${if (index == 0) "完整参数" else "精简参数"}：${failure.message}"
                                if (!failure.shouldRetryMinimal()) break@loop
                            }
                        }
                    }
                    recovered ?: throw AgentModelFailure(
                        code = "IMAGE_GENERATION_FAILED",
                        retryable = false,
                        message = "生图失败，已尝试 ${attempts.size} 次：\n" + attempts.joinToString("\n"),
                    )
                    recovered
                }
                val images = parse(payload, config, controller)
                if (images.isEmpty()) {
                    continuation.resumeWith(Result.failure(
                        AgentModelFailure("IMAGE_EMPTY", true, "服务商返回了空的图片结果"),
                    ))
                } else {
                    continuation.resume(images)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                continuation.cancel(cancelled)
            } catch (failure: Throwable) {
                continuation.resumeWith(Result.failure(failure))
            }
        }
    }

    /** 默认端点：`{baseUrl}/images/generations`。 */
    internal fun defaultEndpoint(provider: ProviderSetting): String {
        val profile = ProviderConnectionProfile.forAuthMode(provider.authMode)
        val base = (profile?.baseUrl ?: provider.baseUrl).trim().trimEnd('/')
        return "$base/images/generations"
    }

    /**
     * 候选端点。有些中转站把 baseUrl 填成 `https://x.com`（不带 /v1），
     * 真正的图像接口在 `/v1/images/generations`；也有站点两条都有。
     * 返回去重后的候选，按可能性从高到低。
     */
    internal fun candidateEndpoints(provider: ProviderSetting): List<String> {
        val profile = ProviderConnectionProfile.forAuthMode(provider.authMode)
        val base = (profile?.baseUrl ?: provider.baseUrl).trim().trimEnd('/')
        val candidates = buildList {
            add("$base/images/generations")
            if (!base.endsWith("/v1")) add("$base/v1/images/generations")
        }
        return candidates.distinct()
    }

    private fun execute(
        provider: ProviderSetting,
        endpoint: String,
        body: String,
        controller: AgentRunController,
    ): String {
        val profile = ProviderConnectionProfile.forAuthMode(provider.authMode)
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(JSON_MEDIA))
            .headers(
                Headers.Builder()
                    .add("Accept", "application/json")
                    .apply {
                        if (profile == null && provider.apiKey.isNotBlank() &&
                            provider.authMode == ProviderAuthModes.API_KEY
                        ) {
                            add("Authorization", "Bearer ${provider.apiKey.trim()}")
                        }
                        ProviderRequestHeaders.mergeInto(
                            this,
                            provider.baseUrl,
                            provider.customHeaders,
                            authMode = provider.authMode,
                        )
                    }
                    .build(),
            )
            .build()
        val client: OkHttpClient = AgentHttpClient.modelClient
        return AuthorizedProviderHttp.execute(
            client = client,
            providerId = provider.id,
            authMode = provider.authMode,
            request = httpRequest,
            controller = controller,
        ) { response ->
            val text = response.peekBody(MAX_RESPONSE_BYTES).string()
            if (!response.isSuccessful) {
                // 生图接口的失败原因几乎都写在正文里（例如 model_not_found、无该模型权限、
                // 渠道不支持该尺寸）。直接带上原始正文，否则只剩一句泛化中文，无法定位。
                throw AgentModelFailure(
                    code = "IMAGE_HTTP_${response.code}",
                    retryable = false,
                    message = "生图接口 HTTP ${response.code}：${text.trim().take(600).ifBlank { "（无响应正文）" }}",
                )
            }
            text
        }
    }

    private fun parse(
        payload: String,
        provider: ProviderSetting,
        controller: AgentRunController,
    ): List<GeneratedImage> {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw AgentModelFailure("IMAGE_BAD_JSON", true, "生图接口返回的不是有效 JSON") }
        root["error"]?.takeIf { it !is JsonNull }?.let { error ->
            val message = error.jsonObjectOrNull()?.get("message")?.jsonPrimitive?.contentOrNull
                ?: error.jsonPrimitive.contentOrNull
                ?: "服务商返回错误"
            throw AgentModelFailure("IMAGE_PROVIDER_ERROR", false, message)
        }
        val data = root["data"]?.jsonObjectOrNull()?.let { listOf(it) }
            ?: root["data"]?.let { runCatching { it.jsonArray }.getOrNull() }
                ?.mapNotNull { it.jsonObjectOrNull() }
            ?: runCatching { listOf(root.jsonObject) }.getOrNull().orEmpty()

        val images = ArrayList<GeneratedImage>(data.size)
        for (item in data) {
            controller.throwIfCancelled()
            val base64 = item["b64_json"]?.jsonPrimitive?.contentOrNull
                ?: item["b64"]?.jsonPrimitive?.contentOrNull
            if (!base64.isNullOrBlank()) {
                val bytes = runCatching { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) }
                    .getOrElse { throw AgentModelFailure("IMAGE_BAD_BASE64", true, "服务商返回的图片数据无法解码") }
                images += GeneratedImage(bytes, sniffMime(bytes))
                continue
            }
            val url = item["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: item["image_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: continue
            images += download(url, provider, controller)
        }
        return images
    }

    private fun download(
        url: String,
        provider: ProviderSetting,
        controller: AgentRunController,
    ): GeneratedImage {
        val request = Request.Builder()
            .url(url)
            .get()
            .apply {
                // 部分中转站把结果放在同域需要鉴权的路径下，带上同样的凭据。
                if (url.startsWith(provider.baseUrl.trim().trimEnd('/'), ignoreCase = true) &&
                    ProviderConnectionProfile.forAuthMode(provider.authMode) == null &&
                    provider.authMode == ProviderAuthModes.API_KEY &&
                    provider.apiKey.isNotBlank()
                ) {
                    header("Authorization", "Bearer ${provider.apiKey.trim()}")
                }
            }
            .build()
        return AuthorizedProviderHttp.execute(
            client = AgentHttpClient.modelClient,
            providerId = provider.id,
            authMode = provider.authMode,
            request = request,
            controller = controller,
        ) { response ->
            if (!response.isSuccessful) {
                throw AgentModelFailure.http(response.code, response.peekBody(4_096).string())
            }
            val declared = response.body?.contentLength() ?: -1L
            if (declared > MAX_IMAGE_BYTES) {
                throw AgentModelFailure("IMAGE_TOO_LARGE", false, "生成的图片超过大小限制")
            }
            val bytes = response.peekBody(MAX_IMAGE_BYTES + 1).bytes()
            if (bytes.isEmpty()) throw AgentModelFailure("IMAGE_EMPTY", true, "图片下载结果为空")
            if (bytes.size > MAX_IMAGE_BYTES) {
                throw AgentModelFailure("IMAGE_TOO_LARGE", false, "生成的图片超过大小限制")
            }
            GeneratedImage(bytes, sniffMime(bytes))
        }
    }

    /** 只看文件头，不信 Content-Type。 */
    private fun sniffMime(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) {
            return "image/webp"
        }
        return "image/png"
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): JsonObject? =
        runCatching { jsonObject }.getOrNull()

    /** 这些状态码先降级重试一次，别直接判死。 */
    private fun AgentModelFailure.shouldRetryMinimal(): Boolean =
        code in setOf("IMAGE_HTTP_400", "IMAGE_HTTP_403", "IMAGE_HTTP_404", "IMAGE_HTTP_422")
}
