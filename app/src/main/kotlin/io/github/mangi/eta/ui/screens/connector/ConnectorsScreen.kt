package io.github.mangi.eta.ui.screens.connector

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.connector.ConnectorConnection
import io.github.mangi.eta.agent.connector.ConnectorRegistry
import io.github.mangi.eta.agent.connector.GitHubConnector
import io.github.mangi.eta.agent.connector.GitHubConnectorManager
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import top.yukonga.miuix.kmp.basic.BasicComponent

/**
 * 授权码在内存里传给详情页，不写入磁盘。
 */
internal data class PendingAuthorization(
    val connectorId: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
    val deviceCode: String,
)

internal object AuthorizationBridge {
    @Volatile
    var pending: PendingAuthorization? = null
}

internal fun loadConnections(context: Context): List<ConnectorConnection> =
    runCatching { GitHubConnectorManager.store(context).connections() }.getOrElse {
        ConnectorRegistry.definitions.map { ConnectorConnection(connectorId = it.id) }
    }

/**
 * 连接器列表页：只做入口与状态展示。
 *
 * 所有授权动作都放在详情页，列表页不再拦一道输入，免得两种 UI 风格各维护一套弹窗。
 */
@Composable
internal fun ConnectorsScreen(
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val definitions = ConnectorRegistry.definitions
    var connections by remember { mutableStateOf(loadConnections(context)) }

    val summaryOf: (ConnectorConnection) -> String = { connection ->
        when {
            !connection.connected -> resources.getString(R.string.connector_disconnected)
            !connection.enabled -> resources.getString(R.string.connector_disabled)
            else -> resources.getString(R.string.connector_connected) + " · " + connection.account
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(
                title = stringResource(R.string.route_connectors),
                onBack = onBack,
            ) {
                Group(title = stringResource(R.string.connector_configured, definitions.size)) {
                    if (definitions.isEmpty()) {
                        ListEmptyState(
                            title = stringResource(R.string.connector_empty_title),
                            summary = stringResource(R.string.connector_empty_summary),
                            icon = Icons.Rounded.Hub,
                        )
                    } else {
                        definitions.forEachIndexed { index, definition ->
                            val connection = connections.firstOrNull { it.connectorId == definition.id }
                                ?: ConnectorConnection(connectorId = definition.id)
                            CouiPreference(
                                title = definition.displayName,
                                summary = summaryOf(connection),
                                onClick = {
                                    connections = loadConnections(context)
                                    onNavigate(AppRoute.ConnectorDetail(definition.id))
                                },
                            )
                            if (index < definitions.lastIndex) Divider()
                        }
                    }
                }
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(
                title = stringResource(R.string.route_connectors),
                onBack = onBack,
            ) {
                item(key = "connectors") {
                    val connection = connections.firstOrNull { it.connectorId == GitHubConnector.ID }
                        ?: ConnectorConnection(connectorId = GitHubConnector.ID)
                    BasicComponent(
                        title = GitHubConnector.definition.displayName,
                        summary = summaryOf(connection),
                        onClick = {
                            connections = loadConnections(context)
                            onNavigate(AppRoute.ConnectorDetail(GitHubConnector.ID))
                        },
                    )
                }
            }
        }
    }
}
