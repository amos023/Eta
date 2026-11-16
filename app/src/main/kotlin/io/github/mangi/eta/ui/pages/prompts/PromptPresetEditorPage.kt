package io.github.mangi.eta.ui.pages.prompts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Group
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.prompt.PromptCustomizationMode
import io.github.mangi.eta.agent.prompt.PromptPreset
import io.github.mangi.eta.agent.prompt.PromptPresetRepository
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage

/**
 * 预设编辑器：可以改预设的全部字段。
 *
 * 内置预设编辑时自动落成用户预设副本，不动内置原文；
 * 保存后回到预设列表。
 */
@Composable
internal fun PromptPresetEditorPage(
    preset: PromptPreset,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    var description by remember { mutableStateOf(preset.description) }
    var mode by remember { mutableStateOf(preset.mode) }
    var base by remember { mutableStateOf(preset.baseSystemPrompt) }
    var personaName by remember { mutableStateOf(preset.personaName) }
    var personaStyle by remember { mutableStateOf(preset.personaStyle) }
    var append by remember { mutableStateOf(preset.appendInstructions) }
    var showNameConflict by remember { mutableStateOf<String?>(null) }

    val isBuiltin = preset.builtin
    val nameChanged = name.trim() != preset.name
    // 改名会覆盖同名用户预设，保存前先确认。
    val conflict = if (nameChanged && !isBuiltin) {
        PromptPresetRepository.imported().any { it.name == name.trim() }
    } else {
        false
    }

    fun doSave() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        PromptPresetRepository.upsert(
            preset.copy(
                name = trimmed,
                description = description,
                mode = mode,
                baseSystemPrompt = base,
                personaName = personaName,
                personaStyle = personaStyle,
                appendInstructions = append,
                builtin = false,
            ),
        )
        // 改名后旧名字的条目还在，删掉避免出现两份。
        if (nameChanged && !isBuiltin) {
            PromptPresetRepository.remove(preset.name)
        }
        onSaved(trimmed)
    }

    fun save() {
        if (conflict) {
            showNameConflict = name.trim()
        } else {
            doSave()
        }
    }

    CouiScaffoldPage(
        title = stringResource(R.string.prompt_preset_editor_title),
        onBack = onBack,
        actions = {
            io.github.mangi.eta.ui.components.coui.CouiTopAppBarTextButton(
                text = stringResource(R.string.prompt_preset_name_save),
                enabled = name.trim().isNotEmpty(),
                onClick = { save() },
            )
        },
    ) {
        if (isBuiltin) {
            Group {
                CouiPreference(
                    title = stringResource(R.string.prompt_preset_editor_builtin_hint),
                    summary = null,
                )
            }
        }

        Group(title = stringResource(R.string.prompt_preset_editor_basic)) {
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_preset_name_placeholder),
                value = name,
                enabled = true,
                singleLine = true,
                onValueChange = { name = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_preset_field_description),
                value = description,
                enabled = true,
                onValueChange = { description = it },
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
                enabled = true,
                onValueChange = { base = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_persona_name),
                value = personaName,
                enabled = true,
                singleLine = true,
                onValueChange = { personaName = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_persona_style),
                value = personaStyle,
                enabled = true,
                onValueChange = { personaStyle = it },
            )
            CouiIconDivider()
            PromptTextFieldRow(
                title = stringResource(R.string.prompt_field_append),
                value = append,
                enabled = true,
                onValueChange = { append = it },
            )
        }

        Group {
            CouiPreference(
                title = stringResource(R.string.prompt_preset_editor_preview),
                summary = stringResource(
                    R.string.prompt_preset_editor_preview_summary,
                    base.length,
                    append.length,
                ),
            )
        }
    }

    showNameConflict?.let { target ->
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.prompt_preset_overwrite_title),
            message = stringResource(R.string.prompt_preset_overwrite_message, target),
            confirmText = stringResource(R.string.prompt_preset_name_save),
            destructive = true,
            onConfirm = {
                showNameConflict = null
                doSave()
            },
            onDismissRequest = { showNameConflict = null },
        )
    }
}
