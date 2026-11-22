package io.github.mangi.eta.agent.tool

import android.content.Context
import android.graphics.BitmapFactory
import io.github.mangi.eta.agent.media.ImageGenerationClient
import io.github.mangi.eta.agent.media.MAX_AGENT_IMAGE_BYTES
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ImageGenerationSettings
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.repository.ProviderRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * 生图工具。
 *
 * 模型选择顺序：调用参数显式指定的模型 id → 设置里的生图模型 → 失败。
 * 不猜聊天模型，避免把对话模型打到图像接口上。
 *
 * 生成的图片同时做两件事：写进应用私有目录 `generated_images/`，作为会话资产
 * 长期保留；同时以 data URL 形式返回给当前模型回合，让它能看到结果。
 */
internal class AgentImageGenerationTools(
    private val context: Context,
    private val controllerProvider: () -> AgentRunController = { AgentRunController() },
) {

    fun generateImage(args: JSONObject): AgentModelClient.ToolResult {
        val prompt = args.optString("prompt").trim()
        if (prompt.isBlank()) {
            return error("INVALID_ARGUMENT", "prompt 不能为空")
        }
        val settings = runBlocking { SettingsDataStore.imageGenerationSettings() }
        val requestedModel = args.optString("model").trim().takeIf { it.isNotBlank() }
            ?: settings.modelId
            ?: return error(
                "IMAGE_MODEL_MISSING",
                "还没有指定生图模型；请在输入栏的生图模型入口选择，或在参数里给出 model",
            )
        val resolved = runBlocking { resolveModel(requestedModel) }
            ?: return error(
                "IMAGE_MODEL_UNAVAILABLE",
                "生图模型 $requestedModel 不可用：服务商或模型已停用，或该模型没有标记生图能力",
            )
        val (provider, model) = resolved

        val size = args.optString("size").trim().takeIf { it.isNotBlank() } ?: settings.size
        val count = args.optInt("count", settings.count).coerceIn(1, 4)
        val quality = args.optString("quality").trim().takeIf { it.isNotBlank() } ?: settings.quality
        if (size.isNotBlank() && size != "auto") {
            val matched = Regex("^\\d{2,5}x\\d{2,5}$").matches(size)
            if (!matched) return error("INVALID_ARGUMENT", "size 必须是 宽x高（例如 1024x1024）或 auto")
        }

        val controller = controllerProvider()
        val images = try {
            runBlocking {
                ImageGenerationClient.generate(
                    request = ImageGenerationClient.RequestSpec(
                        provider = provider,
                        model = model,
                        prompt = prompt,
                        size = size,
                        count = count,
                        quality = quality,
                    ),
                    controller = controller,
                )
            }
        } catch (failure: Throwable) {
            return error(
                "IMAGE_GENERATION_FAILED",
                failure.message ?: failure.javaClass.simpleName,
            )
        }
        if (images.isEmpty()) {
            return error("IMAGE_EMPTY", "服务商没有返回任何图片")
        }

        val savedImages = ArrayList<File>(images.size)
        val modelImages = ArrayList<AgentModelClient.ModelImage>(images.size)
        for ((index, image) in images.withIndex()) {
            if (image.bytes.isEmpty() || image.bytes.size > MAX_AGENT_IMAGE_BYTES) {
                return error("IMAGE_TOO_LARGE", "生成的图片超过大小限制（${image.bytes.size} 字节）")
            }
            val file = runCatching {
                saveImage(
                    bytes = image.bytes,
                    mimeType = image.mimeType,
                    index = index,
                    prompt = prompt,
                    model = model.modelId,
                    size = size,
                    quality = quality,
                )
            }.getOrNull()
                ?: return error("IMAGE_SAVE_FAILED", "生成的图片无法写入本地目录")
            savedImages += file
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
            // 给当前模型回合直接看 data URL，不依赖后续下载或再读盘。
            modelImages += io.github.mangi.eta.agent.media.AgentImageCodec.fromBytes(
                bytes = image.bytes,
                source = "tool_generate_image",
                mimeHint = image.mimeType,
            )
        }

        // 不再自动写相册：图片留在应用私有目录，聊天与画廊都能看到，
        // 需要进相册时由用户点卡片上的「保存」，避免每次出图都往相册里塞。
        return AgentModelClient.ToolResult(
            content = JSONObject()
                .put("ok", true)
                .put("tool", "generate_image")
                .put("model", model.modelId)
                .put("prompt", prompt)
                .put("size", size)
                .put("quality", quality)
                .put("count", savedImages.size)
                .put("images", org.json.JSONArray().also { array ->
                    savedImages.forEach { array.put(it.absolutePath) }
                })
                .toString(),
            images = modelImages,
            // 生成结果属于会话资产，不进敏感工具脱敏路径。
            sensitive = false,
            // 会话资产：聊天里要能反复看到这几张图，路径写进消息记录。
            artifacts = savedImages.map { "file://${it.absolutePath}" },
        )
    }

    private suspend fun resolveModel(modelIdOrModelName: String): Pair<ProviderSetting, Model>? {
        val providers = ProviderRepository.allProviders()
        providers.forEach { provider ->
            if (!provider.isEnabled) return@forEach
            val byId = provider.models.firstOrNull { it.id == modelIdOrModelName && it.isEnabled }
            if (byId != null) return provider to byId
            val byName = provider.models.firstOrNull {
                it.isEnabled && it.modelId == modelIdOrModelName && it.supportsImageGeneration
            }
            if (byName != null) return provider to byName
        }
        return null
    }

    private fun saveImage(
        bytes: ByteArray,
        mimeType: String,
        index: Int,
        prompt: String,
        model: String,
        size: String,
        quality: String,
    ): File {
        val dir = File(context.filesDir, "generated_images")
        if (!dir.isDirectory && !dir.mkdirs()) error("无法创建生图目录")
        val ext = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val base = "img-${System.currentTimeMillis()}-$index"
        val file = File(dir, "$base.$ext")
        file.writeBytes(bytes)
        // 提示词等元数据写成同名 sidecar json：聊天里的「提示词」按钮和历史回看都靠它，
        // 不必为此改数据库结构。写失败只丢提示词，不影响出图。
        runCatching {
            File(dir, "$base.json").writeText(
                JSONObject()
                    .put("prompt", prompt)
                    .put("model", model)
                    .put("size", size)
                    .put("quality", quality)
                    .put("index", index)
                    .put("image", "$base.$ext")
                    .put("createdAt", System.currentTimeMillis())
                    .toString(),
            )
        }
        return file
    }

    private fun error(code: String, message: String): AgentModelClient.ToolResult =
        AgentModelClient.ToolResult(
            content = JSONObject()
                .put("ok", false)
                .put("code", code)
                .put("message", message)
                .toString(),
        )
}
