package io.github.mangi.eta.ui.screens.connector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.connector.ConnectorRegistry
import io.github.mangi.eta.agent.connector.DeviceAuthorization
import io.github.mangi.eta.agent.connector.GitHubConnectorManager
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiGroupItem
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiSectionTitle
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.agent.connector.GitHubConnectorManager.Connected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 把连接后置动作的结果拼成一行可读文字。 */
private fun describeSync(
    resources: android.content.res.Resources,
    connected: Connected,
): String = buildString {
    if (connected.mcpServerId.isNotBlank()) {
        append(resources.getString(R.string.connector_mcp_ok))
    } else {
        append(resources.getString(R.string.connector_mcp_skip))
    }
    if (connected.terminalSynced.isNotEmpty()) {
        append(" · ")
        append(
            resources.getString(
                R.string.connector_sync_terminal_done,
                connected.terminalSynced.joinToString("、"),
            ),
        )
    } else if (connected.terminalError != null) {
        append(" · ")
        append(connected.terminalError)
    } else {
        // 没有任何 Linux 环境就绪：说清楚，别让用户以为已经同步了。
        append(" · ")
        append(resources.getString(R.string.connector_sync_terminal_none))
    }
}

/**
 * 单个连接器详情：只有一条授权通道 —— GitHub 设备码，且不需要用户填任何 Client ID。
 *
 * 点「连接 GitHub」→ 后台申请 8 位码 → 自动跳浏览器 → 用户输码确认
 * → 后台轮询到令牌 → 验证并加密落库 → 状态变已连接。
 */
@Composable
internal fun ConnectorDetailScreen(
    connectorId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val definition = ConnectorRegistry.find(connectorId)
    var connection by remember { mutableStateOf(GitHubConnectorManager.state(context)) }
    var authorization by remember { mutableStateOf(AuthorizationBridge.pending) }
    var polling by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf<String?>(null) }
    var syncToTerminal by remember { mutableStateOf(connection.syncToTerminal) }

    // 环境常在连接之后才装好：进页面时补一次同步，避免凭据永远缺失。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val current = GitHubConnectorManager.state(context)
        if (!current.connected || !current.syncToTerminal) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) { GitHubConnectorManager.resync(context) }
        if (result != null) {
            lastSync = describeSync(resources, result)
            connection = GitHubConnectorManager.state(context)
        }
    }

    fun reload() {
        connection = GitHubConnectorManager.state(context)
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun toastError(failure: Throwable) {
        toast(
            failure.message?.takeIf { it.isNotBlank() }
                ?: resources.getString(R.string.connector_auth_failed)
        )
    }

    val openUrl: (String) -> Unit = { uri ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            .onFailure { toast(resources.getString(R.string.connector_auth_failed)) }
    }

    val copyCode: (String) -> Unit = { code ->
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("EtaConnectorCode", code))
        toast(resources.getString(R.string.connector_code_copied))
    }

    /** 轮询设备码；成功后刷新状态。 */
    val startPolling: (PendingAuthorization) -> Unit = { pending ->
        if (!polling) {
            polling = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        GitHubConnectorManager.awaitAuthorization(
                            context = context,
                            authorization = DeviceAuthorization(
                                userCode = pending.userCode,
                                verificationUri = pending.verificationUri,
                                expiresInSeconds = pending.expiresInSeconds,
                                intervalSeconds = pending.intervalSeconds,
                                deviceCode = pending.deviceCode,
                            ),
                        )
                    }
                }
                polling = false
                result.onSuccess { connected ->
                    AuthorizationBridge.pending = null
                    authorization = null
                    reload()
                    syncToTerminal = connected.connection.syncToTerminal
                    lastSync = describeSync(resources, connected)
                    toast(resources.getString(R.string.connector_auth_success))
                }.onFailure { failure -> toastError(failure) }
            }
        }
    }

    /** 唯一入口：申请设备码 → 跳浏览器 → 开始轮询。全程不需要任何输入。 */
    val connect: () -> Unit = {
        if (!working) {
            working = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { GitHubConnectorManager.beginDeviceAuthorization(context) }
                }
                working = false
                result.onSuccess { device ->
                    val pending = PendingAuthorization(
                        connectorId = connectorId,
                        userCode = device.userCode,
                        verificationUri = device.verificationUri,
                        expiresInSeconds = device.expiresInSeconds,
                        intervalSeconds = device.intervalSeconds,
                        deviceCode = device.deviceCode,
                    )
                    AuthorizationBridge.pending = pending
                    authorization = pending
                    copyCode(device.userCode)
                    openUrl(device.verificationUri)
                    startPolling(pending)
                }.onFailure { failure -> toastError(failure) }
            }
        }
    }

    val disconnect: () -> Unit = {
        showDisconnect = false
        GitHubConnectorManager.disconnect(context)
        AuthorizationBridge.pending = null
        authorization = null
        lastSync = null
        reload()
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiLazyScaffoldPage(
                title = definition?.displayName ?: stringResource(R.string.route_connector_detail),
                onBack = onBack,
                actions = {
                    if (authorization != null) {
                        CouiTopAppBarIconButton(
                            icon = Icons.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.connector_open_github),
                            onClick = { openUrl(authorization!!.verificationUri) },
                        )
                    }
                },
            ) {
                if (definition == null) {
                    item(key = "missing") {
                        ListEmptyState(title = stringResource(R.string.connector_missing))
                    }
                    return@CouiLazyScaffoldPage
                }

                item(key = "status") {
                    Group(title = stringResource(R.string.route_connector_detail)) {
                        CouiPreference(
                            title = stringResource(R.string.connector_account),
                            value = if (connection.connected) {
                                connection.account
                            } else {
                                stringResource(R.string.connector_disconnected)
                            },
                        )
                        Divider()
                        CouiSwitchPreference(
                            title = stringResource(R.string.connector_enable),
                            summary = stringResource(R.string.connector_enable_summary),
                            checked = connection.enabled,
                            onCheckedChange = { enabled ->
                                GitHubConnectorManager.setEnabled(context, enabled)
                                reload()
                            },
                        )
                        Divider()
                        CouiSwitchPreference(
                            title = stringResource(R.string.connector_sync_terminal),
                            summary = stringResource(R.string.connector_sync_terminal_summary),
                            checked = syncToTerminal,
                            onCheckedChange = { enabled ->
                                syncToTerminal = enabled
                                scope.launch(Dispatchers.IO) {
                                    val result = GitHubConnectorManager.setSyncToTerminal(context, enabled)
                                    withContext(Dispatchers.Main) {
                                        lastSync = if (result.error != null) {
                                            result.error
                                        } else if (result.synced.isEmpty()) {
                                            resources.getString(R.string.connector_sync_terminal_none)
                                        } else {
                                            resources.getString(
                                                R.string.connector_sync_terminal_done,
                                                result.synced.joinToString("、"),
                                            )
                                        }
                                        reload()
                                    }
                                }
                            },
                        )
                        if (lastSync != null) {
                            Divider()
                            CouiPreference(
                                title = stringResource(R.string.connector_mcp_registered),
                                summary = lastSync!!,
                            )
                        }
                    }
                }

                if (authorization != null) {
                    item(key = "device-code") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CouiText(
                                text = stringResource(R.string.connector_device_code_title),
                                kind = CouiTextKind.Body,
                            )
                            CouiText(
                                text = authorization!!.userCode,
                                kind = CouiTextKind.GroupTitle,
                            )
                            CouiText(
                                text = stringResource(
                                    R.string.connector_device_code_hint,
                                    authorization!!.verificationUri,
                                ),
                                kind = CouiTextKind.Description,
                            )
                            CouiButton(
                                text = if (polling) {
                                    stringResource(R.string.connector_authorizing)
                                } else {
                                    stringResource(R.string.connector_copy_code)
                                },
                                role = CouiButtonRole.Secondary,
                                onClick = { copyCode(authorization!!.userCode) },
                            )
                            CouiButton(
                                text = stringResource(R.string.connector_open_github),
                                role = CouiButtonRole.Primary,
                                onClick = { openUrl(authorization!!.verificationUri) },
                            )
                            if (!polling) {
                                CouiButton(
                                    text = stringResource(R.string.connector_waiting_authorization),
                                    role = CouiButtonRole.Secondary,
                                    onClick = { startPolling(authorization!!) },
                                )
                            }
                        }
                    }
                }

                item(key = "actions") {
                    Group(title = null) {
                        when {
                            working -> CouiPreference(
                                title = stringResource(R.string.connector_working),
                                summary = stringResource(R.string.connector_working_summary),
                                enabled = false,
                            )

                            !connection.connected -> CouiPreference(
                                title = stringResource(R.string.connector_connect_device_code),
                                summary = stringResource(R.string.connector_connect_device_code_summary),
                                onClick = { connect() },
                            )

                            else -> {
                                CouiPreference(
                                    title = stringResource(R.string.connector_reauth),
                                    summary = stringResource(R.string.connector_tool_scope),
                                    onClick = { connect() },
                                )
                                Divider()
                                CouiPreference(
                                    title = stringResource(R.string.connector_disconnect),
                                    onClick = { showDisconnect = true },
                                )
                            }
                        }
                    }
                }

                item(key = "tools-header") {
                    CouiSectionTitle(
                        text = stringResource(R.string.connector_tools, definition.tools.size),
                    )
                }
                items(definition.tools.size) { index ->
                    val tool = definition.tools[index]
                    CouiGroupItem(
                        isFirst = index == 0,
                        isLast = index == definition.tools.lastIndex,
                    ) {
                        CouiPreference(title = tool.name, summary = tool.description)
                    }
                }
                item(key = "spacer") { CouiPageBottomSpacer() }
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = definition?.displayName ?: stringResource(R.string.route_connector_detail),
                onBack = onBack,
            ) {
                item(key = "status") {
                    SmallTitle(stringResource(R.string.route_connector_detail))
                    BasicComponent(
                        title = stringResource(R.string.connector_account),
                        summary = if (connection.connected) {
                            connection.account
                        } else {
                            stringResource(R.string.connector_disconnected)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.connector_enable),
                        summary = stringResource(R.string.connector_enable_summary),
                        checked = connection.enabled,
                        onCheckedChange = { enabled ->
                            GitHubConnectorManager.setEnabled(context, enabled)
                            reload()
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.connector_sync_terminal),
                        summary = syncToTerminal.takeIf { connection.connected }
                            ?.let { lastSync }
                            ?: stringResource(R.string.connector_sync_terminal_summary),
                        checked = syncToTerminal,
                        onCheckedChange = { enabled ->
                            syncToTerminal = enabled
                            scope.launch(Dispatchers.IO) {
                                val result = GitHubConnectorManager.setSyncToTerminal(context, enabled)
                                withContext(Dispatchers.Main) {
                                    lastSync = if (result.error != null) {
                                        result.error
                                    } else if (result.synced.isEmpty()) {
                                        resources.getString(R.string.connector_sync_terminal_none)
                                    } else {
                                        resources.getString(
                                            R.string.connector_sync_terminal_done,
                                            result.synced.joinToString("、"),
                                        )
                                    }
                                    reload()
                                }
                            }
                        },
                    )
                }

                if (authorization != null) {
                    item(key = "device-code") {
                        SmallTitle(stringResource(R.string.connector_device_code_title))
                        BasicComponent(
                            title = authorization!!.userCode,
                            summary = stringResource(
                                R.string.connector_device_code_hint,
                                authorization!!.verificationUri,
                            ),
                            onClick = { copyCode(authorization!!.userCode) },
                        )
                        BasicComponent(
                            title = stringResource(R.string.connector_open_github),
                            summary = authorization!!.verificationUri,
                            onClick = { openUrl(authorization!!.verificationUri) },
                        )
                        if (!polling) {
                            BasicComponent(
                                title = stringResource(R.string.connector_waiting_authorization),
                                onClick = { startPolling(authorization!!) },
                            )
                        }
                    }
                }

                item(key = "actions") {
                    SmallTitle(stringResource(R.string.route_connector_detail))
                    when {
                        working -> BasicComponent(
                            title = stringResource(R.string.connector_working),
                            summary = stringResource(R.string.connector_working_summary),
                        )

                        !connection.connected -> ArrowPreference(
                            title = stringResource(R.string.connector_connect_device_code),
                            summary = stringResource(R.string.connector_connect_device_code_summary),
                            onClick = { connect() },
                        )

                        else -> {
                            ArrowPreference(
                                title = stringResource(R.string.connector_reauth),
                                summary = stringResource(R.string.connector_tool_scope),
                                onClick = { connect() },
                            )
                            ArrowPreference(
                                title = stringResource(R.string.connector_disconnect),
                                onClick = { showDisconnect = true },
                            )
                        }
                    }
                }

                item(key = "tools") {
                    SmallTitle(stringResource(R.string.connector_tools, definition?.tools?.size ?: 0))
                    definition?.tools?.forEach { tool ->
                        BasicComponent(title = tool.name, summary = tool.description)
                    }
                    Text(
                        text = stringResource(R.string.connector_tool_scope),
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (LocalUiStyle.current == AppearanceUiStyle.COUI) {
        CouiConfirmDialog(
            visible = showDisconnect,
            title = stringResource(R.string.connector_disconnect_title),
            message = stringResource(R.string.connector_disconnect_message),
            confirmText = stringResource(R.string.connector_disconnect),
            destructive = true,
            onConfirm = disconnect,
            onDismissRequest = { showDisconnect = false },
        )
    } else {
        WindowDialog(
            show = showDisconnect,
            title = stringResource(R.string.connector_disconnect_title),
            summary = stringResource(R.string.connector_disconnect_message),
            onDismissRequest = { showDisconnect = false },
        ) {
            MiuixDialogActions(
                confirmText = stringResource(R.string.connector_disconnect),
                destructive = true,
                onCancel = { showDisconnect = false },
                onConfirm = disconnect,
            )
        }
    }
}
