package io.github.mangi.eta.ui.pages.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.itos.coui.layout.Group
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.prompt.PromptCustomizationStore
import io.github.mangi.eta.agent.prompt.PromptVersionRecord
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiIconDivider
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 版本历史页：回滚不复用旧编号。 */
@Composable
internal fun PromptVersionsPage(
    onBack: () -> Unit,
    onRestored: () -> Unit,
) {
    var stored by remember { mutableStateOf(PromptCustomizationStore.load()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    fun rollback(record: PromptVersionRecord) {
        val next = stored.restore(record)
        PromptCustomizationStore.save(next)
        stored = next
        onRestored()
    }

    CouiScaffoldPage(
        title = stringResource(R.string.prompt_versions_title),
        onBack = onBack,
    ) {
        val history = stored.history.filter { it.hasContent }
        if (history.isEmpty()) {
            Group(title = stringResource(R.string.prompt_versions_title)) {
                CouiPreference(
                    title = stringResource(R.string.prompt_versions_empty),
                    summary = null,
                )
            }
        } else {
            Group(title = stringResource(R.string.prompt_versions_title)) {
                history.forEachIndexed { index, record ->
                    if (index > 0) CouiIconDivider()
                    CouiPreference(
                        title = "v${record.version} · ${record.summary}",
                        summary = if (record.updatedAt > 0L) {
                            formatter.format(Date(record.updatedAt))
                        } else {
                            null
                        },
                        value = stringResource(R.string.prompt_versions_restore),
                        onClick = { rollback(record) },
                    )
                }
            }
            Group {
                CouiPreference(
                    title = stringResource(R.string.prompt_versions_clear),
                    summary = stringResource(R.string.prompt_versions_clear_confirm),
                    onClick = { showClearConfirm = true },
                )
            }
        }
    }

    if (showClearConfirm) {
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.prompt_versions_clear),
            message = stringResource(R.string.prompt_versions_clear_confirm),
            confirmText = stringResource(R.string.prompt_versions_clear),
            destructive = true,
            onConfirm = {
                val cleared = stored.clearHistory()
                PromptCustomizationStore.save(cleared)
                stored = cleared
                showClearConfirm = false
            },
            onDismissRequest = { showClearConfirm = false },
        )
    }
}
