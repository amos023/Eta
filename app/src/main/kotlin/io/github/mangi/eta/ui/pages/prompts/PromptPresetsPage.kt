package io.github.mangi.eta.ui.pages.prompts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.prompt.PromptCustomizationStore
import io.github.mangi.eta.agent.prompt.PromptPreset
import io.github.mangi.eta.agent.prompt.PromptPresetCodec
import io.github.mangi.eta.agent.prompt.PromptPresetRepository
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 预设页把选中的预设暂存在这里，编辑页取用后清空。
 *
 * 选预设不等于保存：内容先落在编辑页的草稿里，用户点保存才真正生效。
 */
internal object PromptPresetDraftBridge {
    var pending: PromptPreset? = null
}

/**
 * 提示词预设页。
 *
 * 每一项都能：套用、编辑（改名与内容）、导出、删除。
 * 导入支持 zip 包或裸 JSON；导出把单个预设打包成可分享文件。
 */
@Composable
internal fun PromptPresetsPage(
    onBack: () -> Unit,
    onApplied: () -> Unit,
    onEditPreset: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var builtins by remember { mutableStateOf(PromptPresetRepository.builtins(context)) }
    var imported by remember { mutableStateOf(PromptPresetRepository.imported()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var exportingTarget by remember { mutableStateOf<PromptPreset?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { PromptPresetCodec.read(context, uri) }
            when (result) {
                is PromptPresetCodec.Result.Ok -> {
                    imported = PromptPresetRepository.addImported(result.value)
                    statusText = context.getString(R.string.prompt_presets_imported, result.value.name)
                }
                is PromptPresetCodec.Result.Failure -> {
                    statusText = context.getString(R.string.prompt_presets_import_failed, result.reason)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val target = exportingTarget
        exportingTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                PromptPresetCodec.write(context, uri, target)
            }
            statusText = when (result) {
                is PromptPresetCodec.Result.Ok ->
                    context.getString(R.string.prompt_presets_exported_named, target.name)
                is PromptPresetCodec.Result.Failure ->
                    context.getString(R.string.prompt_presets_export_failed, result.reason)
            }
        }
    }

    fun apply(preset: PromptPreset) {
        // 只把预设内容交给编辑页当草稿，不直接落盘。
        // 是否生效由用户在编辑页点「保存」决定，避免选个预设就把当前配置改掉。
        PromptPresetDraftBridge.pending = preset
        onApplied()
    }

    CouiScaffoldPage(
        title = stringResource(R.string.prompt_presets_title),
        onBack = onBack,
    ) {
        statusText?.let { text ->
            CouiSectionTitle(text = text)
        }

        Group {
            CouiPreference(
                title = stringResource(R.string.prompt_presets_import),
                summary = stringResource(R.string.prompt_presets_import_summary),
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/json",
                            "text/plain",
                            "text/markdown",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
            )
        }

        Group {
            builtins.forEachIndexed { index, preset ->
                if (index > 0) CouiIconDivider()
                PresetRow(
                    preset = preset,
                    onApply = { apply(preset) },
                    onEdit = { onEditPreset(preset.name) },
                    onExport = {
                        exportingTarget = preset
                        exportLauncher.launch("${preset.name}.zip")
                    },
                    onDelete = null,
                )
            }
        }

        if (imported.isNotEmpty()) {
            Group {
                imported.forEachIndexed { index, preset ->
                    if (index > 0) CouiIconDivider()
                    PresetRow(
                        preset = preset,
                        onApply = { apply(preset) },
                        onEdit = { onEditPreset(preset.name) },
                        onExport = {
                            exportingTarget = preset
                            exportLauncher.launch("${preset.name}.zip")
                        },
                        onDelete = { pendingDelete = preset.name },
                    )
                }
            }
        }
    }

    pendingDelete?.let { name ->
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.prompt_presets_delete_title),
            message = stringResource(R.string.prompt_presets_delete_message, name),
            confirmText = stringResource(R.string.prompt_presets_delete_short),
            destructive = true,
            onConfirm = {
                imported = PromptPresetRepository.remove(name)
                pendingDelete = null
            },
            onDismissRequest = { pendingDelete = null },
        )
    }
}

/**
 * 单个预设行：主体点击套用，右侧三个文字操作。
 *
 * 用自定义 Row 而不是 CouiPreference：后者只支持一个 trailing 区域，
 * 这里要同时放编辑 / 导出 / 删除三个入口。
 */
@Composable
private fun PresetRow(
    preset: PromptPreset,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = CouiPageDefaults.themeColors()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onApply,
            )
            .padding(
                horizontal = CouiPageDefaults.rowPadding,
                vertical = 12.dp,
            ),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = preset.name,
                kind = CouiTextKind.PreferenceTitle,
                color = colors.title,
            )
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    kind = CouiTextKind.PreferenceSummary,
                    color = colors.summary,
                )
            }
        }
        Text(
            text = stringResource(R.string.prompt_presets_edit),
            kind = CouiTextKind.Description,
            color = colors.accent,
            modifier = Modifier
                .clickable(interactionSource = interaction, indication = null, onClick = onEdit)
                .padding(horizontal = 4.dp),
        )
        Text(
            text = stringResource(R.string.prompt_presets_export_short),
            kind = CouiTextKind.Description,
            color = colors.accent,
            modifier = Modifier
                .clickable(interactionSource = interaction, indication = null, onClick = onExport)
                .padding(horizontal = 4.dp),
        )
        if (onDelete != null) {
            Text(
                text = stringResource(R.string.prompt_presets_delete_short),
                kind = CouiTextKind.Description,
                color = colors.summary,
                modifier = Modifier
                    .clickable(interactionSource = interaction, indication = null, onClick = onDelete)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
