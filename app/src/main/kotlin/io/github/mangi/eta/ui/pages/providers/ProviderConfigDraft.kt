package io.github.mangi.eta.ui.pages.providers

import androidx.compose.runtime.saveable.mapSaver
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.CustomHeaderFilter
import io.github.mangi.eta.data.model.GeminiProviderSetting
import io.github.mangi.eta.data.model.AnthropicProviderSetting
import io.github.mangi.eta.data.model.CustomHeader
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.CustomProviderSetting
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.provider.BuiltinProviders
import io.github.mangi.eta.data.provider.ProviderConnectionProfile
import io.github.mangi.eta.data.provider.ProviderSourceRegistry
import io.github.mangi.eta.ui.navigation.NewProviderType
import java.util.UUID

internal data class ProviderHeaderDraft(
    val id: String = UUID.randomUUID().toString(),
    val header: CustomHeader = CustomHeader("", ""),
)

internal data class ProviderConfigDraft(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val systemPrompt: String,
    val isEnabled: Boolean,
    val endpointMode: String,
    val hostedWebSearchEnabled: Boolean,
    val anthropicVersion: String,
    val headers: List<ProviderHeaderDraft> = emptyList(),
    val models: List<Model> = emptyList(),
    val authMode: String = ProviderAuthModes.API_KEY,
    /** 生图专用提供商：拉取模型时只保留生图模型，列表页归入生图分组。 */
    val imageOnly: Boolean = false,
    /** 当前选中的提供商类型；新建时可在页内切换，已存在的提供商固定为自身类型。 */
    val providerType: NewProviderType = NewProviderType.OpenAiCompatible,
) {
    companion object {
        fun from(provider: ProviderSetting): ProviderConfigDraft {
            val profile = ProviderConnectionProfile.forAuthMode(provider.authMode)
            return ProviderConfigDraft(
                models = provider.models,
                headers = provider.customHeaders.map { ProviderHeaderDraft(header = it) },
                name = provider.name,
                baseUrl = profile?.baseUrl ?: provider.baseUrl,
                apiKey = provider.apiKey,
                authMode = provider.authMode,
                systemPrompt = provider.systemPrompt.orEmpty(),
                isEnabled = provider.isEnabled,
                endpointMode = profile?.endpointMode ?: when (provider) {
                    is OpenAiCompatibleProviderSetting -> provider.endpointMode
                    is CustomProviderSetting -> provider.endpointMode
                    is AnthropicProviderSetting, is GeminiProviderSetting -> ""
                },
                hostedWebSearchEnabled = provider.hostedWebSearchEnabled,
                anthropicVersion = (provider as? AnthropicProviderSetting)?.anthropicVersion
                    ?: AnthropicProviderSetting.DEFAULT_ANTHROPIC_VERSION,
                imageOnly = provider.imageOnly,
                providerType = provider.toNewProviderType(),
            )
        }
    }
}

internal val ProviderConfigDraftSaver = mapSaver(
    save = { draft ->
        mapOf(
            "models" to kotlinx.serialization.json.Json.encodeToString(draft.models),
            "headers" to ArrayList(draft.headers.flatMap { listOf(it.id, it.header.name, it.header.value) }),
            "name" to draft.name,
            "baseUrl" to draft.baseUrl,
            "apiKey" to draft.apiKey,
            "authMode" to draft.authMode,
            "systemPrompt" to draft.systemPrompt,
            "isEnabled" to draft.isEnabled,
            "endpointMode" to draft.endpointMode,
            "hostedWebSearchEnabled" to draft.hostedWebSearchEnabled,
            "anthropicVersion" to draft.anthropicVersion,
            "imageOnly" to draft.imageOnly,
            "providerType" to draft.providerType.name,
        )
    },
    restore = { state ->
        ProviderConfigDraft(
            models = (state["models"] as? String)?.let {
                runCatching { kotlinx.serialization.json.Json.decodeFromString<List<Model>>(it) }.getOrNull()
            }.orEmpty(),
            headers = (state["headers"] as? List<*>)?.chunked(3)?.map {
                ProviderHeaderDraft(it[0] as String, CustomHeader(it[1] as String, it[2] as String))
            }.orEmpty(),
            name = state.getValue("name") as String,
            baseUrl = state.getValue("baseUrl") as String,
            apiKey = state.getValue("apiKey") as String,
            authMode = state["authMode"] as? String ?: ProviderAuthModes.API_KEY,
            systemPrompt = state.getValue("systemPrompt") as String,
            isEnabled = state.getValue("isEnabled") as Boolean,
            endpointMode = state.getValue("endpointMode") as String,
            hostedWebSearchEnabled = state.getValue("hostedWebSearchEnabled") as Boolean,
            anthropicVersion = state.getValue("anthropicVersion") as String,
            imageOnly = state["imageOnly"] as? Boolean ?: false,
            providerType = (state["providerType"] as? String)
                ?.let { name -> runCatching { NewProviderType.valueOf(name) }.getOrNull() }
                ?: NewProviderType.OpenAiCompatible,
        )
    },
)

internal fun buildUpdatedProvider(
    source: ProviderSetting,
    name: String,
    baseUrl: String,
    apiKey: String,
    systemPrompt: String,
    isEnabled: Boolean,
    endpointMode: String,
    hostedWebSearchEnabled: Boolean,
    anthropicVersion: String,
    customHeaders: List<CustomHeader>,
    authMode: String = source.authMode,
    imageOnly: Boolean = source.imageOnly,
): ProviderSetting {
    val prompt = systemPrompt.trim().takeIf { it.isNotBlank() }
    val profile = ProviderConnectionProfile.forAuthMode(authMode)
    return when (source) {
        is OpenAiCompatibleProviderSetting -> source.copy(
            customHeaders = customHeaders.map { it.copy(name = it.name.trim()) },
            name = name.trim(),
            baseUrl = profile?.baseUrl ?: baseUrl.trim(),
            apiKey = apiKey.trim(),
            authMode = authMode,
            systemPrompt = prompt,
            isEnabled = isEnabled,
            endpointMode = profile?.endpointMode ?: endpointMode,
            hostedWebSearchEnabled = hostedWebSearchEnabled,
            imageOnly = imageOnly,
        )
        is CustomProviderSetting -> source.copy(
            customHeaders = customHeaders.map { it.copy(name = it.name.trim()) },
            name = name.trim(),
            baseUrl = profile?.baseUrl ?: baseUrl.trim(),
            apiKey = apiKey.trim(),
            authMode = authMode,
            systemPrompt = prompt,
            isEnabled = isEnabled,
            endpointMode = profile?.endpointMode ?: endpointMode,
            hostedWebSearchEnabled = hostedWebSearchEnabled,
            imageOnly = imageOnly,
        )
        is GeminiProviderSetting -> source.copy(
            customHeaders = customHeaders.map { it.copy(name = it.name.trim()) },
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            systemPrompt = prompt,
            isEnabled = isEnabled,
            hostedWebSearchEnabled = hostedWebSearchEnabled,
        )
        is AnthropicProviderSetting -> source.copy(
            customHeaders = customHeaders.map { it.copy(name = it.name.trim()) },
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            systemPrompt = prompt,
            isEnabled = isEnabled,
            anthropicVersion = anthropicVersion.trim().ifBlank { AnthropicProviderSetting.DEFAULT_ANTHROPIC_VERSION },
        )
    }
}

/** 提供商实际类型，用于回填草稿里的类型选择与判断页内字段显隐。 */
internal fun ProviderSetting.toNewProviderType(): NewProviderType = when (this) {
    is AnthropicProviderSetting -> NewProviderType.Anthropic
    is GeminiProviderSetting -> NewProviderType.Gemini
    is OpenAiCompatibleProviderSetting, is CustomProviderSetting ->
        if (imageOnly) NewProviderType.ImageOnly else NewProviderType.OpenAiCompatible
}

/** 切换类型：保留已填的名称、API Key 与自定义 Base URL，类型专属默认值跟着换。 */
internal fun ProviderConfigDraft.withProviderType(target: NewProviderType): ProviderConfigDraft {
    if (target == providerType) return this
    fun defaultBaseUrl(type: NewProviderType): String = when (type) {
        NewProviderType.Anthropic -> ANTHROPIC_DEFAULT_BASE_URL
        NewProviderType.Gemini -> GeminiProviderSetting.DEFAULT_BASE_URL
        NewProviderType.OpenAiCompatible, NewProviderType.ImageOnly -> ""
    }
    val previousDefault = defaultBaseUrl(providerType)
    val customUrl = baseUrl.trim().takeIf { it.isNotBlank() && it != previousDefault }
    val geminiOrAnthropic = target == NewProviderType.Anthropic || target == NewProviderType.Gemini
    return copy(
        providerType = target,
        baseUrl = customUrl ?: defaultBaseUrl(target),
        imageOnly = target == NewProviderType.ImageOnly,
        authMode = if (geminiOrAnthropic || target == NewProviderType.ImageOnly) {
            ProviderAuthModes.API_KEY
        } else {
            authMode
        },
        endpointMode = if (geminiOrAnthropic) "" else OpenAiEndpointMode.CHAT_COMPLETIONS,
        hostedWebSearchEnabled = if (target == NewProviderType.Gemini) hostedWebSearchEnabled else false,
    )
}

internal const val ANTHROPIC_DEFAULT_BASE_URL = "https://api.anthropic.com"

/**
 * 按草稿当前选中的类型产出提供商设置。
 *
 * 类型与 [source] 一致时沿用 copy 路径，保留模型列表、内置标记等字段；
 * 类型被切换时按目标类型重建实例，名称、API Key 等通用字段继续带过去。
 */
internal fun ProviderConfigDraft.toProvider(source: ProviderSetting): ProviderSetting =
    if (providerType == source.toNewProviderType()) {
        buildUpdatedProvider(
            source = source,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            systemPrompt = systemPrompt,
            isEnabled = isEnabled,
            endpointMode = endpointMode,
            hostedWebSearchEnabled = hostedWebSearchEnabled,
            anthropicVersion = anthropicVersion,
            customHeaders = headers.map { it.header },
            authMode = authMode,
            imageOnly = imageOnly,
        )
    } else {
        buildSwitchedProvider(source)
    }

private fun ProviderConfigDraft.buildSwitchedProvider(source: ProviderSetting): ProviderSetting {
    val prompt = systemPrompt.trim().takeIf { it.isNotBlank() }
    val trimmedHeaders = headers.map { it.header.copy(name = it.header.name.trim()) }
    val profile = ProviderConnectionProfile.forAuthMode(authMode)
    val resolvedBaseUrl = profile?.baseUrl ?: baseUrl.trim()
    return when (providerType) {
        NewProviderType.Anthropic -> AnthropicProviderSetting(
            id = source.id,
            name = name.trim(),
            baseUrl = resolvedBaseUrl.ifBlank { ANTHROPIC_DEFAULT_BASE_URL },
            apiKey = apiKey.trim(),
            isEnabled = isEnabled,
            isBuiltIn = source.isBuiltIn,
            sortOrder = source.sortOrder,
            systemPrompt = prompt,
            models = source.models,
            customHeaders = trimmedHeaders,
            customBody = source.customBody,
            createdAt = source.createdAt,
            anthropicVersion = anthropicVersion.trim()
                .ifBlank { AnthropicProviderSetting.DEFAULT_ANTHROPIC_VERSION },
            authMode = authMode,
        )
        NewProviderType.Gemini -> GeminiProviderSetting(
            id = source.id,
            name = name.trim(),
            baseUrl = resolvedBaseUrl.ifBlank { GeminiProviderSetting.DEFAULT_BASE_URL },
            apiKey = apiKey.trim(),
            isEnabled = isEnabled,
            isBuiltIn = source.isBuiltIn,
            sortOrder = source.sortOrder,
            systemPrompt = prompt,
            models = source.models,
            customHeaders = trimmedHeaders,
            customBody = source.customBody,
            createdAt = source.createdAt,
            hostedWebSearchEnabled = hostedWebSearchEnabled,
        )
        NewProviderType.OpenAiCompatible, NewProviderType.ImageOnly -> CustomProviderSetting(
            id = source.id,
            name = name.trim(),
            baseUrl = resolvedBaseUrl,
            apiKey = apiKey.trim(),
            isEnabled = isEnabled,
            isBuiltIn = source.isBuiltIn,
            sortOrder = source.sortOrder,
            systemPrompt = prompt,
            models = source.models,
            customHeaders = trimmedHeaders,
            customBody = source.customBody,
            createdAt = source.createdAt,
            endpointMode = profile?.endpointMode ?: endpointMode,
            hostedWebSearchEnabled = hostedWebSearchEnabled,
            authMode = authMode,
            imageOnly = providerType == NewProviderType.ImageOnly,
        )
    }
}

internal fun providerAccountAuthMode(provider: ProviderSetting): String? {
    if (provider is AnthropicProviderSetting || provider is GeminiProviderSetting) return null
    if (ProviderAuthModes.isOAuth(provider.authMode)) return provider.authMode
    return when (ProviderSourceRegistry.resolve(provider)) {
        ProviderSourceTypes.OPENAI -> ProviderAuthModes.OPENAI_CHATGPT
        ProviderSourceTypes.XAI -> ProviderAuthModes.XAI_GROK
        else -> null
    }
}

internal fun ProviderConfigDraft.withAuthMode(mode: String, provider: ProviderSetting): ProviderConfigDraft {
    val profile = ProviderConnectionProfile.forAuthMode(mode)
    if (profile != null) return copy(
        authMode = mode,
        baseUrl = profile.baseUrl,
        endpointMode = profile.endpointMode,
        hostedWebSearchEnabled = hostedWebSearchEnabled && profile.endpointMode == OpenAiEndpointMode.RESPONSES,
    )
    val original = ProviderConfigDraft.from(provider)
    if (provider.authMode == ProviderAuthModes.API_KEY) return copy(
        authMode = mode,
        baseUrl = original.baseUrl,
        endpointMode = original.endpointMode,
    )
    val apiProvider = checkNotNull(BuiltinProviders.providerById(
        if (authMode == ProviderAuthModes.XAI_GROK) BuiltinProviders.XAI_ID else BuiltinProviders.OPENAI_ID,
    ))
    return copy(
        authMode = mode,
        baseUrl = apiProvider.baseUrl,
        endpointMode = ProviderConfigDraft.from(apiProvider).endpointMode,
    )
}

internal fun validateProviderDraft(context: android.content.Context, draft: ProviderConfigDraft): String? {
    if (draft.name.isBlank()) return context.getString(R.string.page_name_cannot_be_empty_ca8984)
    val uri = runCatching { java.net.URI(draft.baseUrl.trim()) }.getOrNull()
    if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
        return context.getString(R.string.page_base_url_must_be_a_valid_http_s_address_0e7d58)
    }
    return CustomHeaderFilter.validationError(draft.headers.map { it.header })
}
