package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.itos.coui.layout.Divider
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.SystemTts
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.GeminiTtsModel
import io.github.mangi.eta.data.model.MiniMaxTtsModel
import io.github.mangi.eta.data.model.SpeechCredentialField
import io.github.mangi.eta.data.model.SystemTtsConfig
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
internal fun SystemTtsConnectionSection(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val config = store.settings.systemTts
    val engines = remember(context) { SystemTts.engines(context) }
    val labels = listOf(stringResource(R.string.speech_tts_engine_default)) + engines.map { it.label }
    val selected = engines.indexOfFirst { it.name == config.engine }.let { if (it >= 0) it + 1 else 0 }
    val onSelect: (Int) -> Unit = { index ->
        val name = engines.getOrNull(index - 1)?.name.orEmpty()
        store.edit(store.settings.copy(systemTts = config.copy(engine = name)))
    }
    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_connection)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    CouiDropdownPreference(
                        title = stringResource(R.string.speech_tts_engine),
                        items = labels,
                        selectedIndex = selected,
                        onSelectedIndexChange = onSelect,
                    )
                    Divider()
                }
                AppearanceUiStyle.MIUIX -> OverlayDropdownPreference(
                    title = stringResource(R.string.speech_tts_engine),
                    items = labels,
                    selectedIndex = selected,
                    onSelectedIndexChange = onSelect,
                )
            }
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_tts_language_hint),
                    value = config.language,
                    onChange = { store.edit(store.settings.copy(systemTts = config.copy(language = it.trim()))) },
                )
            }
        }
        SpeechNote(
            if (engines.isEmpty()) stringResource(R.string.speech_tts_system_missing)
            else stringResource(R.string.speech_tts_system_note),
        )
    }
}

@Composable
internal fun GeminiConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.geminiTts
    val items = listOf(
        stringResource(R.string.speech_gemini_flash),
        stringResource(R.string.speech_gemini_flash_lite),
    )
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.geminiTts,
        onBaseUrl = { store.edit(store.settings.copy(geminiTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.GEMINI_TTS, it) },
        note = stringResource(R.string.speech_gemini_connection_note),
    ) {
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiDropdownPreference(
                    title = stringResource(R.string.speech_tts_model),
                    items = items,
                    selectedIndex = config.model.ordinal,
                    onSelectedIndexChange = { index ->
                        GeminiTtsModel.entries.getOrNull(index)?.let {
                            store.edit(store.settings.copy(geminiTts = config.copy(model = it)))
                        }
                    },
                )
                Divider()
            }
            AppearanceUiStyle.MIUIX -> {
                OverlayDropdownPreference(
                    title = stringResource(R.string.speech_tts_model),
                    items = items,
                    selectedIndex = config.model.ordinal,
                    onSelectedIndexChange = { index ->
                        GeminiTtsModel.entries.getOrNull(index)?.let {
                            store.edit(store.settings.copy(geminiTts = config.copy(model = it)))
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun MiniMaxConnectionSection(store: SpeechSettingsStore) {
    val config = store.settings.minimaxTts
    val items = listOf(
        stringResource(R.string.speech_minimax_turbo),
        stringResource(R.string.speech_minimax_hd),
    )
    SpeechApiKeyConnectionSection(
        baseUrl = config.baseUrl,
        apiKey = store.credentials.minimaxTts,
        onBaseUrl = { store.edit(store.settings.copy(minimaxTts = config.copy(baseUrl = it))) },
        onApiKey = { store.credential(SpeechCredentialField.MINIMAX_TTS, it) },
        note = stringResource(R.string.speech_minimax_connection_note),
    ) {
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiDropdownPreference(
                    title = stringResource(R.string.speech_tts_model),
                    items = items,
                    selectedIndex = config.model.ordinal,
                    onSelectedIndexChange = { index ->
                        MiniMaxTtsModel.entries.getOrNull(index)?.let {
                            store.edit(store.settings.copy(minimaxTts = config.copy(model = it)))
                        }
                    },
                )
                Divider()
            }
            AppearanceUiStyle.MIUIX -> {
                OverlayDropdownPreference(
                    title = stringResource(R.string.speech_tts_model),
                    items = items,
                    selectedIndex = config.model.ordinal,
                    onSelectedIndexChange = { index ->
                        MiniMaxTtsModel.entries.getOrNull(index)?.let {
                            store.edit(store.settings.copy(minimaxTts = config.copy(model = it)))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SpeechApiKeyConnectionSection(
    baseUrl: String,
    apiKey: String,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    note: String,
    model: @Composable () -> Unit,
) {
    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_connection)) {
            model()
            SpeechFieldColumn {
                SpeechField("API Key", apiKey, secret = true, onChange = { onApiKey(it.trim()) })
                SpeechField(stringResource(R.string.speech_base_url), baseUrl, onChange = { onBaseUrl(it.trim()) })
            }
        }
        SpeechNote(note)
    }
}

@Composable
internal fun SpeechDeliveryFields(store: SpeechSettingsStore) {
    when (store.settings.tts) {
        TtsProvider.GEMINI -> {
            val config = store.settings.geminiTts
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_gemini_style),
                    value = config.style,
                    singleLine = false,
                    onChange = { store.edit(store.settings.copy(geminiTts = config.copy(style = it))) },
                )
            }
        }
        TtsProvider.MINIMAX -> {
            val config = store.settings.minimaxTts
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                .plus(config.speed).distinct().sorted()
            val items = speeds.map { "$it×" }
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    Divider()
                    CouiDropdownPreference(
                        title = stringResource(R.string.speech_speed),
                        items = items,
                        selectedIndex = speeds.indexOf(config.speed),
                        onSelectedIndexChange = { index ->
                            speeds.getOrNull(index)?.let {
                                store.edit(store.settings.copy(minimaxTts = config.copy(speed = it)))
                            }
                        },
                    )
                }
                AppearanceUiStyle.MIUIX -> {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.speech_speed),
                        items = items,
                        selectedIndex = speeds.indexOf(config.speed),
                        onSelectedIndexChange = { index ->
                            speeds.getOrNull(index)?.let {
                                store.edit(store.settings.copy(minimaxTts = config.copy(speed = it)))
                            }
                        },
                    )
                }
            }
        }
        TtsProvider.SYSTEM -> SystemTtsDeliveryFields(store)
        TtsProvider.NONE, TtsProvider.QWEN, TtsProvider.DOUBAO -> Unit
    }
}

/** 系统引擎只暴露语速与音调；音色由引擎自身与系统设置决定。 */
@Composable
private fun SystemTtsDeliveryFields(store: SpeechSettingsStore) {
    val config = store.settings.systemTts
    val rateItems = SystemTtsConfig.RATE_OPTIONS.map { "${it}×" }
    val pitchItems = SystemTtsConfig.PITCH_OPTIONS.map { "${it}×" }
    val onRate: (Int) -> Unit = { index ->
        SystemTtsConfig.RATE_OPTIONS.getOrNull(index)?.let {
            store.edit(store.settings.copy(systemTts = config.copy(rate = it)))
        }
    }
    val onPitch: (Int) -> Unit = { index ->
        SystemTtsConfig.PITCH_OPTIONS.getOrNull(index)?.let {
            store.edit(store.settings.copy(systemTts = config.copy(pitch = it)))
        }
    }
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiDropdownPreference(
                title = stringResource(R.string.speech_speed),
                items = rateItems,
                selectedIndex = SystemTtsConfig.RATE_OPTIONS.indexOf(config.rate).coerceAtLeast(0),
                onSelectedIndexChange = onRate,
            )
            Divider()
            CouiDropdownPreference(
                title = stringResource(R.string.speech_pitch),
                items = pitchItems,
                selectedIndex = SystemTtsConfig.PITCH_OPTIONS.indexOf(config.pitch).coerceAtLeast(0),
                onSelectedIndexChange = onPitch,
            )
        }
        AppearanceUiStyle.MIUIX -> {
            OverlayDropdownPreference(
                title = stringResource(R.string.speech_speed),
                items = rateItems,
                selectedIndex = SystemTtsConfig.RATE_OPTIONS.indexOf(config.rate).coerceAtLeast(0),
                onSelectedIndexChange = onRate,
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.speech_pitch),
                items = pitchItems,
                selectedIndex = SystemTtsConfig.PITCH_OPTIONS.indexOf(config.pitch).coerceAtLeast(0),
                onSelectedIndexChange = onPitch,
            )
        }
    }
}
