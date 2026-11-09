package io.github.mangi.eta.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Divider
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.voice.LocalSpeechModel
import io.github.mangi.eta.agent.voice.LocalSpeechModelStatus
import io.github.mangi.eta.agent.voice.validateSpeechSettings
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiDropdownPreference
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SpeechRecognitionScreen(onBack: () -> Unit, onOpenOss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SpeechSettingsStore(context, scope) }
    SpeechLifecycle { store.stop() }
    val settings = store.settings
    val asrLabels = AsrProvider.entries.map { it.label() }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.speech_recognition_title),
                onBack = onBack,
            ) {
                if (!store.loaded) {
                    SpeechLoadingState(store.message)
                    return@CouiScaffoldPage
                }
                SpeechGroupCard {
                    CouiSwitchPreference(
                        title = stringResource(R.string.speech_recognition_enabled),
                        checked = settings.recognitionEnabled,
                        onCheckedChange = { store.edit(settings.copy(recognitionEnabled = it)) },
                    )
                }
                if (settings.recognitionEnabled) {
                    SpeechGroupCard(title = stringResource(R.string.speech_group_service)) {
                        CouiDropdownPreference(
                            title = stringResource(R.string.speech_service_and_model),
                            items = asrLabels,
                            selectedIndex = settings.asr.ordinal,
                            onSelectedIndexChange = { index ->
                                AsrProvider.entries.getOrNull(index)?.let { store.edit(settings.copy(asr = it)) }
                            },
                        )
                    }
                    SpeechNote(
                        stringResource(
                            when (settings.asr) {
                                AsrProvider.QWEN_REALTIME, AsrProvider.DOUBAO -> R.string.speech_asr_note_streaming
                                AsrProvider.QWEN_FLASH -> R.string.speech_asr_note_flash
                                AsrProvider.QWEN_FILE -> R.string.speech_asr_note_file
                                AsrProvider.LOCAL -> R.string.speech_asr_note_local
                            },
                        ),
                    )
                    SpeechConnectionSection(store, synthesis = false)
                    if (settings.asr == AsrProvider.LOCAL) {
                        LocalAsrCard(store)
                    } else if (settings.asr != AsrProvider.DOUBAO) {
                        SpeechGroupCard {
                            SpeechFieldColumn {
                                SpeechField(
                                    label = stringResource(R.string.speech_language_hint),
                                    value = settings.language,
                                    onChange = { store.edit(settings.copy(language = it.trim())) },
                                )
                            }
                        }
                    }
                    SpeechResultSection(store)
                    if (settings.asr == AsrProvider.QWEN_FILE) {
                        SpeechGroupCard {
                            CouiPreference(
                                title = stringResource(R.string.speech_oss_title),
                                summary = settings.oss.bucket.ifBlank { stringResource(R.string.speech_oss_configure) },
                                onClick = onOpenOss,
                            )
                        }
                    }
                    SpeechRecognitionTestSection(store)
                } else {
                    SpeechNote(stringResource(R.string.speech_recognition_disabled_note))
                }
                SpeechSaveBlock(store) {
                    validateSpeechSettings(store.settings, store.credentials, synthesis = false)
                }
                CouiPageBottomSpacer()
            }
        }
        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.speech_recognition_title),
                onBack = onBack,
                modifier = Modifier.imePadding(),
            ) {
                if (!store.loaded) {
                    item(key = "loading") { SpeechLoadingState(store.message) }
                    return@MiuixScaffoldPage
                }
                item(key = "recognition_enabled") {
                    SpeechGroupCard {
                        SwitchPreference(
                            title = stringResource(R.string.speech_recognition_enabled),
                            checked = settings.recognitionEnabled,
                            onCheckedChange = { store.edit(settings.copy(recognitionEnabled = it)) },
                        )
                    }
                }
                if (settings.recognitionEnabled) {
                    item(key = "service") {
                        Column {
                            SpeechGroupCard(title = stringResource(R.string.speech_group_service)) {
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.speech_service_and_model),
                                    items = asrLabels,
                                    selectedIndex = settings.asr.ordinal,
                                    onSelectedIndexChange = { index ->
                                        AsrProvider.entries.getOrNull(index)?.let { store.edit(settings.copy(asr = it)) }
                                    },
                                )
                            }
                            SpeechNote(
                                stringResource(
                                    when (settings.asr) {
                                        AsrProvider.QWEN_REALTIME, AsrProvider.DOUBAO -> R.string.speech_asr_note_streaming
                                        AsrProvider.QWEN_FLASH -> R.string.speech_asr_note_flash
                                        AsrProvider.QWEN_FILE -> R.string.speech_asr_note_file
                                        AsrProvider.LOCAL -> R.string.speech_asr_note_local
                                    },
                                ),
                            )
                        }
                    }
                    item(key = "connection") {
                        SpeechConnectionSection(store, synthesis = false)
                    }
                    if (settings.asr == AsrProvider.LOCAL) {
                        item(key = "local") { LocalAsrCard(store) }
                    } else if (settings.asr != AsrProvider.DOUBAO) {
                        item(key = "language") {
                            SpeechGroupCard {
                                SpeechFieldColumn {
                                    SpeechField(
                                        label = stringResource(R.string.speech_language_hint),
                                        value = settings.language,
                                        onChange = { store.edit(settings.copy(language = it.trim())) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "result") {
                        SpeechResultSection(store)
                    }
                    if (settings.asr == AsrProvider.QWEN_FILE) {
                        item(key = "oss") {
                            SpeechGroupCard {
                                ArrowPreference(
                                    title = stringResource(R.string.speech_oss_title),
                                    summary = settings.oss.bucket.ifBlank { stringResource(R.string.speech_oss_configure) },
                                    onClick = onOpenOss,
                                )
                            }
                        }
                    }
                    item(key = "test") {
                        SpeechRecognitionTestSection(store)
                    }
                } else {
                    item(key = "recognition_disabled_note") {
                        SpeechNote(stringResource(R.string.speech_recognition_disabled_note))
                    }
                }
                item(key = "save") {
                    SpeechSaveBlock(store) {
                        validateSpeechSettings(store.settings, store.credentials, synthesis = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechResultSection(store: SpeechSettingsStore) {
    val settings = store.settings
    val streaming = settings.asr != AsrProvider.QWEN_FLASH && settings.asr != AsrProvider.QWEN_FILE
    val options = SpeechSettings.AUTO_SEND_SILENCE_OPTIONS
    val optionLabels = options.map { millis ->
        if (millis == 0) {
            stringResource(R.string.speech_auto_send_off)
        } else {
            stringResource(R.string.speech_auto_send_seconds, (millis / 1_000f).toString().removeSuffix(".0"))
        }
    }
    val selectedOptionIndex = options.indexOf(settings.autoSendSilenceMs)
        .takeIf { it >= 0 }
        ?: options.indexOf(SpeechSettings.DEFAULT_AUTO_SEND_SILENCE_MS)
    val uiStyle = LocalUiStyle.current

    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_result)) {
            if (streaming) {
                if (uiStyle == AppearanceUiStyle.COUI) {
                    CouiDropdownPreference(
                        title = stringResource(R.string.speech_auto_send_title),
                        items = optionLabels,
                        selectedIndex = selectedOptionIndex,
                        onSelectedIndexChange = { index ->
                            options.getOrNull(index)?.let { store.edit(settings.copy(autoSendSilenceMs = it)) }
                        },
                    )
                    Divider()
                } else {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.speech_auto_send_title),
                        items = optionLabels,
                        selectedIndex = selectedOptionIndex,
                        onSelectedIndexChange = { index ->
                            options.getOrNull(index)?.let { store.edit(settings.copy(autoSendSilenceMs = it)) }
                        },
                    )
                }
            }
            if (uiStyle == AppearanceUiStyle.COUI) {
                CouiSwitchPreference(
                    title = stringResource(R.string.speech_refine_title),
                    summary = stringResource(R.string.speech_refine_summary),
                    checked = settings.refineTranscript,
                    onCheckedChange = { store.edit(settings.copy(refineTranscript = it)) },
                )
            } else {
                SwitchPreference(
                    title = stringResource(R.string.speech_refine_title),
                    summary = stringResource(R.string.speech_refine_summary),
                    checked = settings.refineTranscript,
                    onCheckedChange = { store.edit(settings.copy(refineTranscript = it)) },
                )
            }
        }
        if (streaming) SpeechNote(stringResource(R.string.speech_auto_send_note))
    }
}

@Composable
private fun SpeechRecognitionTestSection(store: SpeechSettingsStore) {
    val state by store.recognition.state.collectAsState()
    val request = rememberSpeechPermission { store.recognition.start(store.settings, store.credentials) }
    val summary = state.error
        ?: state.preview.take(200).ifBlank { state.progress }.ifBlank { null }
    val title = stringResource(if (state.active) R.string.speech_test_finish else R.string.speech_test_recognition)

    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_test_recognition)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    CouiPreference(
                        title = title,
                        summary = summary,
                        summaryColor = if (state.error != null) StatusError else Color.Unspecified,
                        enabled = !store.saving,
                        showChevron = false,
                        trailing = if (state.active) {
                            {
                                CouiButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { store.recognition.cancel() },
                                    minHeight = 32.dp,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            null
                        },
                        onClick = { if (state.active) store.recognition.finish() else request() },
                    )
                }
                AppearanceUiStyle.MIUIX -> {
                    BasicComponent(
                        title = title,
                        summary = summary,
                        summaryColor = BasicComponentDefaults.summaryColor(
                            color = if (state.error != null) StatusError else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        ),
                        enabled = !store.saving,
                        onClick = { if (state.active) store.recognition.finish() else request() },
                        endActions = if (state.active) {
                            {
                                TextButton(
                                    text = stringResource(R.string.action_cancel),
                                    onClick = { store.recognition.cancel() },
                                    minWidth = 0.dp,
                                    minHeight = 32.dp,
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
        }
        SpeechNote(stringResource(R.string.speech_test_note))
    }
}

/** 内置离线模型按需下载：先展示模型状态与下载入口，再提供推理线程与下载源。 */
@Composable
private fun LocalAsrCard(store: SpeechSettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model by LocalSpeechModel.state.collectAsState()
    val settings = store.settings
    val config = settings.localAsr
    val size = stringResource(R.string.speech_local_size, LocalSpeechModel.TOTAL_BYTES / 1024 / 1024)
    LaunchedEffect(Unit) { LocalSpeechModel.refresh(context) }

    val summary = when {
        model.status == LocalSpeechModelStatus.DOWNLOADING ->
            stringResource(R.string.speech_local_downloading, model.progress)
        model.status == LocalSpeechModelStatus.READY ->
            stringResource(R.string.speech_local_ready, model.current.ifBlank { size })
        model.error != null -> model.error
        else -> stringResource(R.string.speech_local_missing, size)
    }
    val actionTitle = stringResource(
        if (model.status == LocalSpeechModelStatus.READY) R.string.speech_local_delete
        else R.string.speech_local_download,
    )
    val onAction: () -> Unit = {
        when {
            model.busy -> Unit
            model.status == LocalSpeechModelStatus.READY -> scope.launch {
                LocalSpeechModel.delete(context)
            }
            else -> scope.launch {
                runCatching { LocalSpeechModel.download(context, config.sourceUrl) }
            }
        }
    }

    Column {
        SpeechGroupCard(title = stringResource(R.string.speech_group_local_model)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> CouiPreference(
                    title = actionTitle,
                    summary = summary,
                    summaryColor = if (model.error != null) MiuixTheme.colorScheme.error else Color.Unspecified,
                    enabled = !model.busy,
                    showChevron = false,
                    onClick = onAction,
                )
                AppearanceUiStyle.MIUIX -> BasicComponent(
                    title = actionTitle,
                    summary = summary,
                    summaryColor = if (model.error != null) {
                        BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.error)
                    } else {
                        BasicComponentDefaults.summaryColor()
                    },
                    enabled = !model.busy,
                    onClick = onAction,
                )
            }
        }
        val threads = listOf(1, 2, 3, 4)
        val items = threads.map { stringResource(R.string.speech_local_threads_value, it.toString()) }
        val selected = threads.indexOf(config.threads).coerceAtLeast(0)
        val onSelected: (Int) -> Unit = { index ->
            threads.getOrNull(index)?.let { value ->
                store.edit(settings.copy(localAsr = config.copy(threads = value)))
            }
        }
        SpeechGroupCard(title = stringResource(R.string.speech_group_local)) {
            when (LocalUiStyle.current) {
                AppearanceUiStyle.COUI -> {
                    CouiDropdownPreference(
                        title = stringResource(R.string.speech_local_threads),
                        items = items,
                        selectedIndex = selected,
                        onSelectedIndexChange = onSelected,
                    )
                    Divider()
                }
                AppearanceUiStyle.MIUIX -> OverlayDropdownPreference(
                    title = stringResource(R.string.speech_local_threads),
                    items = items,
                    selectedIndex = selected,
                    onSelectedIndexChange = onSelected,
                )
            }
            SpeechFieldColumn {
                SpeechField(
                    label = stringResource(R.string.speech_local_source),
                    value = config.sourceUrl,
                    onChange = { store.edit(settings.copy(localAsr = config.copy(sourceUrl = it.trim()))) },
                )
            }
        }
        SpeechNote(stringResource(R.string.speech_local_note, size))
    }
}
