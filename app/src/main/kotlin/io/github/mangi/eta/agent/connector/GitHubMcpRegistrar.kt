package io.github.mangi.eta.agent.connector

import android.content.Context
import io.github.mangi.eta.data.model.McpAuthorizationType
import io.github.mangi.eta.data.model.McpProtocolMode
import io.github.mangi.eta.data.model.McpServerSetting
import io.github.mangi.eta.data.repository.McpServerRepository
import org.json.JSONObject

/**
 * 连接 GitHub 后自动登记官方远程 MCP 服务器。
 *
 * 设备码拿到的 token 直接当 Bearer 用，MCP 那边就能提供写操作能力
 * （提 issue、开 PR、合并、代码搜索等），比本地那四个只读工具强得多。
 *
 * 服务器 id 记在连接状态里，断开时据此精确删除，不会误删用户手工添加的条目。
 */
internal object GitHubMcpRegistrar {
    const val SERVER_NAME = "GitHub"
    const val SERVER_URL = "https://api.githubcopilot.com/mcp/"

    /** 登记或更新；返回服务器 id。已经存在同名同 URL 的条目时复用，避免重复。 */
    suspend fun register(context: Context, token: String): String {
        val existing = McpServerRepository.servers()
            .firstOrNull { it.url.trimEnd('/') == SERVER_URL.trimEnd('/') }
        if (existing != null) {
            McpServerRepository.update(
                existing.copy(
                    name = SERVER_NAME,
                    enabled = true,
                    authorizationType = McpAuthorizationType.BEARER,
                ),
                bearerToken = token,
            )
            return existing.id
        }
        val added = McpServerRepository.add(
            McpServerSetting(
                id = "",
                name = SERVER_NAME,
                url = SERVER_URL,
                enabled = true,
                protocolMode = McpProtocolMode.AUTO,
                authorizationType = McpAuthorizationType.BEARER,
            ),
            bearerToken = token,
        )
        return added.id
    }

    /** 断开时删除自动登记的那条；用户自己加的服务器不动。 */
    suspend fun unregister(serverId: String?) {
        if (serverId.isNullOrBlank()) return
        runCatching { McpServerRepository.delete(serverId) }
    }

    /** 令牌刷新后把新的写回 MCP 密文，不需要重建条目。 */
    suspend fun refreshToken(serverId: String, token: String) {
        if (serverId.isBlank()) return
        val server = McpServerRepository.serverById(serverId) ?: return
        McpServerRepository.update(
            server.copy(authorizationType = McpAuthorizationType.BEARER),
            bearerToken = token,
        )
    }

    fun describe(): String = JSONObject()
        .put("server", SERVER_NAME)
        .put("url", SERVER_URL)
        .toString()
}
