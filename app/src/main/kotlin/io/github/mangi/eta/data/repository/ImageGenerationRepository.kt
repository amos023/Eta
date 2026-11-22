package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.ImageGenerationSettings
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ProviderSetting
import kotlinx.coroutines.flow.Flow

/**
 * 生图配置仓库。
 *
 * 只负责「选哪个模型、默认多大、几张」这一件事；实际请求在 ImageGenerationClient。
 * 模型引用统一用本地 `Model.id`，与服务商/模型停用联动：停用后自动视为未选。
 */
internal object ImageGenerationRepository {

    data class ResolvedModel(val provider: ProviderSetting, val model: Model)

    fun settingsFlow(): Flow<ImageGenerationSettings> = SettingsDataStore.imageGenerationSettingsFlow()

    suspend fun settings(): ImageGenerationSettings = SettingsDataStore.imageGenerationSettings()

    suspend fun save(settings: ImageGenerationSettings) =
        SettingsDataStore.setImageGenerationSettings(settings)

    suspend fun selectModel(modelId: String?) {
        val current = settings()
        save(current.copy(modelId = modelId))
    }

    suspend fun selectSize(size: String) {
        val current = settings()
        save(current.copy(size = size))
    }

    suspend fun selectCount(count: Int) {
        val current = settings()
        save(current.copy(count = count.coerceIn(1, 4)))
    }

    suspend fun selectQuality(quality: String) {
        val current = settings()
        save(current.copy(quality = quality))
    }

    suspend fun setAutoSaveToGallery(enabled: Boolean) {
        val current = settings()
        save(current.copy(autoSaveToGallery = enabled))
    }

    /** 选中的生图模型是否仍然可用；不可用时返回 null，由调用方退回未选状态。 */
    fun resolve(modelId: String?, providers: List<ProviderSetting>): ResolvedModel? {
        val id = modelId?.takeIf { it.isNotBlank() } ?: return null
        providers.forEach { provider ->
            if (!provider.isEnabled) return@forEach
            val model = provider.models.firstOrNull {
                it.id == id && it.isEnabled && it.supportsImageGeneration
            }
            if (model != null) return ResolvedModel(provider, model)
        }
        return null
    }

    /** 第一版自动兜底：用户没选时，取排序最前的可用生图模型。 */
    fun firstAvailable(providers: List<ProviderSetting>): ResolvedModel? {
        providers.forEach { provider ->
            if (!provider.isEnabled) return@forEach
            val model = provider.models
                .filter { it.isEnabled && it.supportsImageGeneration }
                .minByOrNull { it.sortOrder }
            if (model != null) return ResolvedModel(provider, model)
        }
        return null
    }
}