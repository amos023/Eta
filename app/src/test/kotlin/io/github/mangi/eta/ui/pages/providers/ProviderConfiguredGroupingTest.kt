package io.github.mangi.eta.ui.pages.providers

import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.ProviderAuthModes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 已配置分组的判据：既要有模型，也要有可用凭据。
 * 内置提供商自带预置模型但没有 Key，这类占位项必须留在未配置分组。
 */
class ProviderConfiguredGroupingTest {

    @Test
    fun providerWithModelsButNoApiKeyIsNotConfigured() {
        val provider = provider(models = 7, apiKey = "")
        assertFalse("内置预置模型但没有 Key 不算已配置", provider.hasUsableCredentials())
    }

    @Test
    fun providerWithApiKeyIsConfigured() {
        val provider = provider(models = 4, apiKey = "sk-test")
        assertTrue(provider.hasUsableCredentials())
    }

    @Test
    fun oauthProviderIsConfiguredWithoutApiKey() {
        val provider = provider(models = 2, apiKey = "", authMode = ProviderAuthModes.OPENAI_CHATGPT)
        assertTrue("OAuth 凭据不在 apiKey 字段", provider.hasUsableCredentials())
    }

    @Test
    fun localProxyWithoutKeyIsNotConfigured() {
        val provider = provider(
            models = 0,
            apiKey = "",
            baseUrl = "http://127.0.0.1:8080/v1",
        )
        assertFalse("零模型的本地代理也属于未配置", provider.hasUsableCredentials())
    }

    private fun provider(
        models: Int,
        apiKey: String,
        baseUrl: String = "https://api.example.com/v1",
        authMode: String = ProviderAuthModes.API_KEY,
    ) = OpenAiCompatibleProviderSetting(
        id = "test",
        name = "测试提供商",
        baseUrl = baseUrl,
        apiKey = apiKey,
        authMode = authMode,
        models = List(models) { index ->
            Model(id = "m-$index", modelId = "m-$index", displayName = "模型 $index")
        },
    )
}
