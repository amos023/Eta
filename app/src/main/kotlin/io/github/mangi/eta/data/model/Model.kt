package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Model(
    val id: String,
    val modelId: String,
    val displayName: String,
    val ownedBy: String? = null,
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
    val contextWindow: Int? = null,
    val contextWindowOverride: Int? = null,
    val inputModalities: List<String> = listOf(TEXT_MODALITY),
    val outputModalities: List<String> = listOf(TEXT_MODALITY),
    val attachment: Boolean? = null,
    val toolCall: Boolean? = null,
    val reasoning: Boolean? = null,
    val reasoningCapabilities: ModelReasoningCapabilities? = null,
    val reasoningOverride: Boolean? = null,
    val reasoningCapabilitiesOverride: ModelReasoningCapabilities? = null,
    val structuredOutput: Boolean? = null,
    val supportsTemperature: Boolean? = null,
    /** 生图能力：true 表示该模型用于图像生成而不是对话。 */
    val imageGeneration: Boolean? = null,
    /** 用户手改的生图能力，优先级高于远端值与推断值。 */
    val imageGenerationOverride: Boolean? = null,
    /** 覆盖生图接口地址；为空时按服务商 baseUrl 拼 images/generations。 */
    val imageEndpoint: String? = null,
    /** 默认出图尺寸，例如 1024x1024；为空时用服务商默认档。 */
    val defaultImageSize: String? = null,
    /** 默认出图格式：png / jpeg / webp。 */
    val defaultImageFormat: String? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
    val source: ModelSource = ModelSource.MANUAL,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 生效的上下文窗口：手改值优先，否则用远端/预设填入的声明值。 */
    val effectiveContextWindow: Int?
        get() = contextWindowOverride ?: contextWindow

    val effectiveReasoning: Boolean?
        get() = reasoningOverride ?: reasoning

    val effectiveReasoningCapabilities: ModelReasoningCapabilities?
        get() = when (reasoningOverride) {
            false -> null
            true -> reasoningCapabilitiesOverride ?: ModelReasoningCapabilities()
            null -> reasoningCapabilities
        }

    val supportsVision: Boolean
        get() = attachment == true || inputModalities.any { it.equals(IMAGE_MODALITY, ignoreCase = true) }

    val supportsTools: Boolean
        get() = toolCall == true

    val supportsReasoning: Boolean
        get() = effectiveReasoning == true

    val supportsImageGeneration: Boolean
        get() = when {
            imageGenerationOverride != null -> imageGenerationOverride
            imageGeneration != null -> imageGeneration
            else -> looksLikeImageGenerationModel
        }

    /**
     * 历史数据兜底：早期版本把 Z-Image 等生图模型写成了 image_generation = NULL，
     * 只改拉取分类不会回填已有记录，这里按 id 特征再判一次。
     * 仅当用户或远端都没有明确表态时生效，显式给定值优先。
     */
    private val looksLikeImageGenerationModel: Boolean
        get() {
            if (imageGeneration != null || imageGenerationOverride != null) return false
            val id = modelId.lowercase()
            return IMAGE_MODEL_ID_HINTS.any { it in id }
        }

    /** 该模型能否参与对话：生图模型不进入聊天模型选择。 */
    val isChatModel: Boolean
        get() = !supportsImageGeneration

    companion object {
        const val TEXT_MODALITY = "text"
        const val IMAGE_MODALITY = "image"

        /** 与 RemoteModelFetcher 的生图特征保持一致的兜底判定集合。 */
        private val IMAGE_MODEL_ID_HINTS = listOf(
            "dall-e", "gpt-image", "flux", "stable-diffusion", "sdxl",
            "wanx", "cogview", "kolors", "seedream", "hidream",
            "image-generation", "text-to-image", "txt2img", "imagen",
            "qwen-image", "doubao-seedream", "midjourney", "nijijourney",
            "z-image", "image",
        )
    }
}

@Serializable
enum class ModelSource {
    MANUAL,
    REMOTE,
    CATALOG,
}
