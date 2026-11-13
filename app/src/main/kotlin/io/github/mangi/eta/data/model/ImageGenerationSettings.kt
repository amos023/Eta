package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * 生图配置。
 *
 * [modelId] 是本地 `Model.id`（不是远端 modelId 字符串），为空表示还没选生图模型，
 * 此时不出现任何生图入口，避免拿聊天模型去调图像接口。
 *
 * [size] 与 [count] 是输入栏的默认档位，用户可以逐次覆盖。
 */
@Serializable
internal data class ImageGenerationSettings(
    val modelId: String? = null,
    val size: String = DEFAULT_SIZE,
    val count: Int = DEFAULT_COUNT,
    /**
     * 生成后自动存进系统相册。
     *
     * 应用私有目录会被清理、卸载时也会一起消失，默认开着让产物落在用户自己能找到的地方；
     * 不想污染相册就在生图菜单里关掉，图仍留在会话里可看。
     */
    val autoSaveToGallery: Boolean = true,
) {
    companion object {
        const val DEFAULT_SIZE = "1024x1024"
        const val DEFAULT_COUNT = 1

        /** 常见尺寸档位；自定义尺寸交给服务商自己校验。 */
        val SIZE_PRESETS = listOf(
            "1024x1024",
            "1024x1536",
            "1536x1024",
            "512x512",
            "768x1024",
            "1024x768",
            "1280x720",
            "720x1280",
            "auto",
        )

        val COUNT_PRESETS = listOf(1, 2, 3, 4)
    }
}