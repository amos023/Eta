package io.github.mangi.eta.agent.connector

import org.json.JSONArray
import org.json.JSONObject

/** 连接器种类；二期新增 MCP 适配器时管理页不需要改动。 */
internal enum class ConnectorKind {
    /** 直接用 App 内置的 API 客户端，不需要 OAuth。 */
    BUILTIN_API,

    /** 需要用户授权后拿到访问令牌的第三方服务。 */
    OAUTH_API,

    /** 预留：把 MCP Server 当作连接器展示。 */
    MCP_SERVER,
}

/** 一个连接器对外声明的工具；parametersJson 必须是 JSON Schema 字符串。 */
internal data class ConnectorToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
)

/** 连接器静态定义，只在代码里声明，不落库。 */
internal data class ConnectorDefinition(
    val id: String,
    val kind: ConnectorKind,
    val displayName: String,
    val description: String,
    val tools: List<ConnectorToolDefinition>,
    /** 一期所有连接器都是只读；写操作预留。 */
    val readOnly: Boolean = true,
)

/** 连接器本地连接状态；令牌不在这里，单独用 Keystore 加密保存。 */
internal data class ConnectorConnection(
    val connectorId: String,
    val connected: Boolean = false,
    val enabled: Boolean = true,
    val account: String = "",
    val avatarUrl: String = "",
    val scopes: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    /** 自动登记到 MCP 列表的服务器 id；断开时据此精确清理。 */
    val mcpServerId: String = "",
    /** 是否把同一份凭据同步进 Linux 环境的 git / gh。 */
    val syncToTerminal: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("connected", connected)
        .put("enabled", enabled)
        .put("account", account)
        .put("avatarUrl", avatarUrl)
        .put("scopes", JSONArray().also { array -> scopes.forEach(array::put) })
        .put("updatedAt", updatedAt)
        .put("mcpServerId", mcpServerId)
        .put("syncToTerminal", syncToTerminal)

    companion object {
        fun from(id: String, json: JSONObject?): ConnectorConnection {
            if (json == null) return ConnectorConnection(connectorId = id)
            val scopes = mutableListOf<String>()
            val raw = json.optJSONArray("scopes")
            if (raw != null) {
                for (index in 0 until raw.length()) {
                    val item = raw.optString(index).trim()
                    if (item.isNotEmpty()) scopes += item
                }
            }
            return ConnectorConnection(
                connectorId = id,
                connected = json.optBoolean("connected", false),
                enabled = json.optBoolean("enabled", true),
                account = json.optString("account", ""),
                avatarUrl = json.optString("avatarUrl", ""),
                scopes = scopes,
                updatedAt = json.optLong("updatedAt", 0L),
                mcpServerId = json.optString("mcpServerId", ""),
                syncToTerminal = json.optBoolean("syncToTerminal", true),
            )
        }
    }
}

/** 连接器注册表：一期只有 GitHub，二期加别的服务只改这里。 */
internal object ConnectorRegistry {
    val definitions: List<ConnectorDefinition> = listOf(GitHubConnector.definition)

    fun find(id: String): ConnectorDefinition? = definitions.firstOrNull { it.id == id }

    fun allToolNames(): Set<String> =
        definitions.flatMapTo(mutableSetOf()) { definition -> definition.tools.map { it.name } }
}
