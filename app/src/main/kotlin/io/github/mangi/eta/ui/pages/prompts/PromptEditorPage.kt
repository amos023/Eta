package io.github.mangi.eta.ui.pages.prompts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import com.itos.coui.layout.Group
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.prompt.PromptCustomization
import io.github.mangi.eta.agent.prompt.PromptCustomizationMode
import io.github.mangi.eta.agent.prompt.PromptCustomizationStore
import io.github.mangi.eta.agent.prompt.PromptPreset
import io.github.mangi.eta.agent.prompt.PromptPresetRepository
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarTextButton

/**
 * 提示词编辑页。
 *
 * 所有修改只改草稿，点「保存」才落盘并生效；有未保存改动时返回会提示并留在此页。
 * 从预设页 / 版本页 / 生效页返回时重新读取存储，保证看到的是当前生效内容。
 */
@Composable
internal fun PromptEditorPage(
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenVersions: () -> Unit,
    onOpenEffective: () -> Unit,
) {
    val context = LocalContext.current
    var stored by remember { mutableStateOf(PromptCustomizationStore.load()) }
    var enabled by remember { mutableStateOf(stored.enabled) }
    var mode by remember { mutableStateOf(stored.mode) }
    var base by remember { mutableStateOf(stored.baseSystemPrompt) }
    var personaName by remember { mutableStateOf(stored.personaName) }
    var personaStyle by remember { mutableStateOf(stored.personaStyle) }
    var append by remember { mutableStateOf(stored.appendInstructions) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var showSaveAsPreset by remember { mutableStateOf(false) }
    var presetNameDraft by remember { mutableStateOf("") }
    var presetDescDraft by remember { mutableStateOf("") }

    fun reload() {
        val latest = PromptCustomizationStore.load()
        stored = latest
        enabled = latest.enabled
        mode = latest.mode
        base = latest.baseSystemPrompt
        personaName = latest.personaName
        personaStyle = latest.personaStyle
        append = latest.appendInstructions
        statusText = null
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 预设页选了预设：只填进草稿，不落盘。用户点保存才生效。
                PromptPresetDraftBridge.pending?.let { preset ->
                    PromptPresetDraftBridge.pending = null
                    enabled = true
                    mode = preset.mode
                    base = preset.baseSystemPrompt
                    personaName = preset.personaName
                    personaStyle = preset.personaStyle
                    append = preset.appendInstructions
                    statusText = context.getString(R.string.prompt_preset_loaded, preset.name)
                    return@LifecycleEventObserver
                }
                // 从子页面回来时拉一次最新值，但不要覆盖用户正在编辑的草稿：
                // 只有内容与已保存版本完全一致（即没有改动）时才同步。
                val latest = PromptCustomizationStore.load()
                val noDraft =
                    base == stored.baseSystemPrompt &&
                        personaName == stored.personaName &&
                        personaStyle == stored.personaStyle &&
                        append == stored.appendInstructions &&
                        enabled == stored.enabled &&
                        mode == stored.mode
                if (noDraft) {
                    stored = latest
                    enabled = latest.enabled
                    mode = latest.mode
                    base = latest.baseSystemPrompt
                    personaName = latest.personaName
                    personaStyle = latest.personaStyle
                    append = latest.appendInstructions
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dirty = enabled != stored.enabled ||
        mode != stored.mode ||
        base != stored.baseSystemPrompt ||
        personaName != stored.personaName ||
        personaStyle != stored.personaStyle ||
        append != stored.appendInstructions

    fun save() {
        val next = stored.edit(
            enabled = enabled,
            mode = mode,
            baseSystemPrompt = base,
            personaName = personaName,
            personaStyle = personaStyle,
            appendInstructions = append,
        )
        if (PromptCustomizationStore.save(next)) {
            stored = next
            statusText = context.getString(R.string.prompt_saved, next.version)
        } else {
            statusText = context.getString(R.string.prompt_save_failed)
        }
    }

    CouiScaffoldPage(
        title = stringResource(R.string.prompt_page_title),
        // 有未保存改动也直接离开：草稿丢弃，已保存内容不变。
        // 保存与否完全由顶栏「保存」决定，不在这里拦一道。
        onBack = onBack,
        actions = {
            // 两个入口都用图标：「另存」放文字会把标题挤没，实测顶栏放不下两个字。
            CouiTopAppBarIconButton(
                icon = Icons.Rounded.BookmarkAdd,
                contentDescription = stringResource(R.string.prompt_action_save_as_preset),
                onClick = {
                    presetNameDraft = ""
                    presetDescDraft = ""
                    showSaveAsPreset = true
                },
            )
            CouiTopAppBarTextButton(
                text = stringResource(R.string.prompt_action_save),
                enabled = dirty,
                onClick = { if (dirty) save() },
            )
        },
    ) {
        statusText?.let { text ->
            Group {
                CouiPreference(title = text, summary = null)
            }
        }

        Group(title = stringResource(R.string.prompt_section_switch)) {
            CouiSwitchPreference(
                title = stringResource(R.string.prompt_enable_title),
                summary = stringResource(R.string.prompt_enable_summary),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            CouiIconDivider()
            CouiPreference(
                title = stringResource(R.string.prompt_mode_title),
                summary = stringResource(
                    if (mode == PromptCustomizationMode.REPLACE) {
                        R.string.prompt_mode_replace_summary
                    } else {
                        R.string.prompt_mode_append_summary
                    },
                ),
                value = stringResource(
                    if (mode == PromptCustomizationMode.REPLACE) {
                        R.string.prompt_mode_replace
                    } else {
                        R.string.prompt_mode_append
                    },
                ),
                onClick = {
                    mode = if (mode == PromptCustomizationMode.REPLACE) {
                        PromptCustomizationMode.APPEND
                    } else {
                        PromptCustomizationMode.REPLACE
                    }
                },
            )
        }

        Group(title = stringResource(R.string.prompt_section_content)) {
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_base),
                value = base,
                enabled = enabled,
                onValueChange = { base = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_persona_name),
                value = personaName,
                enabled = enabled,
                singleLine = true,
                onValueChange = { personaName = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_persona_style),
                value = personaStyle,
                enabled = enabled,
                onValueChange = { personaStyle = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_append),
                value = append,
                enabled = enabled,
                onValueChange = { append = it },
            )
        }

        Group(title = stringResource(R.string.prompt_section_verify)) {
            CouiPreference(
                title = stringResource(R.string.prompt_action_effective),
                summary = stringResource(R.string.prompt_action_effective_summary),
                onClick = onOpenEffective,
            )
        }

        Group(title = stringResource(R.string.prompt_section_manage)) {
            CouiPreference(
                title = stringResource(R.string.prompt_action_presets),
                summary = stringResource(R.string.prompt_action_presets_summary),
                onClick = onOpenPresets,
            )
            CouiIconDivider()
            CouiPreference(
                title = stringResource(R.string.prompt_action_versions),
                summary = stringResource(R.string.prompt_action_versions_summary, stored.version),
                onClick = onOpenVersions,
            )
            CouiIconDivider()
            CouiPreference(
                title = stringResource(R.string.prompt_action_reset),
                summary = stringResource(R.string.prompt_action_reset_summary),
                onClick = {
                    val cleared = stored.resetToDefault()
                    PromptCustomizationStore.save(cleared)
                    reload()
                    statusText = context.getString(R.string.prompt_reset_done)
                },
            )
        }

    }

    if (showSaveAsPreset) {
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.prompt_action_save_as_preset),
            message = null,
            confirmText = stringResource(R.string.prompt_preset_name_save),
            confirmEnabled = presetNameDraft.trim().isNotEmpty(),
            onConfirm = {
                val name = presetNameDraft.trim()
                if (name.isNotEmpty()) {
                    PromptPresetRepository.upsert(
                        PromptPreset(
                            name = name,
                            description = presetDescDraft.trim(),
                            mode = mode,
                            baseSystemPrompt = base,
                            personaName = personaName,
                            personaStyle = personaStyle,
                            appendInstructions = append,
                        ),
                    )
                    statusText = context.getString(R.string.prompt_preset_saved_as, name)
                }
                showSaveAsPreset = false
            },
            onDismissRequest = { showSaveAsPreset = false },
        ) {
            CouiTextField(
                value = presetNameDraft,
                onValueChange = { presetNameDraft = it },
                label = stringResource(R.string.prompt_preset_name_placeholder),
                singleLine = true,
                showClearButton = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CouiTextField(
                value = presetDescDraft,
                onValueChange = { presetDescDraft = it },
                label = stringResource(R.string.prompt_preset_field_description),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                showClearButton = false,
            )
        }
    }

}
