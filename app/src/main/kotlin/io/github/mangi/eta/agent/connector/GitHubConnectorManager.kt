package io.github.mangi.eta.agent.connector

import android.content.Context
import org.json.JSONObject

/**
 * GitHub 授权流程编排：申请设备码 → 用户到浏览器确认 → 轮询拿令牌 → 拉账号信息
 * → 自动登记 MCP → 同步终端 git → 落库。
 *
 * 不需要用户提供任何 Client ID：内置的公开 OAuth App 凭据由 [GitHubDeviceFlow] 持有。
 * 所有阻塞调用都要在 IO 线程上跑。
 */
internal object GitHubConnectorManager {

    /** 连接成功后的副作用摘要，方便 UI 展示到底同步成了什么。 */
    data class Connected(
        val connection: ConnectorConnection,
        val mcpServerId: String = "",
        val terminalSynced: List<String> = emptyList(),
        val terminalSkipped: List<String> = emptyList(),
        val terminalError: String? = null,
    )

    fun store(context: Context): ConnectorStore = ConnectorStore(context)

    fun state(context: Context): ConnectorConnection = ConnectorStore(context).connection(GitHubConnector.ID)

    /** 申请一次性授权码；点一下就能拿到，无需任何输入。 */
    fun beginDeviceAuthorization(context: Context): DeviceAuthorization =
        GitHubDeviceFlow.requestDeviceCode()

    /**
     * 轮询直到授权完成、被拒绝或超时，成功后跑完所有连接后置动作。
     *
     * 用退避而不是固定间隔：GitHub 要求遵守 interval，遇到 slow_down 就继续按间隔走，
     * 避免被判定为滥用。
     */
    fun awaitAuthorization(
        context: Context,
        authorization: DeviceAuthorization,
        onPending: () -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Connected {
        val store = ConnectorStore(context)
        val deadline = System.currentTimeMillis() + authorization.expiresInSeconds * 1000L
        var interval = authorization.intervalSeconds
        while (System.currentTimeMillis() < deadline) {
            if (isCancelled()) {
                throw GitHubAuthException("CONNECTOR_AUTH_CANCELLED", "授权已取消")
            }
            val token = GitHubDeviceFlow.pollOnce(authorization.deviceCode)
            if (token != null) {
                return completeAuthorization(context, store, token)
            }
            onPending()
            interval = (interval + 1).coerceAtMost(15)
            Thread.sleep(interval * 1000L)
        }
        throw GitHubAuthException("CONNECTOR_DEVICE_CODE_EXPIRED", "授权已超时，请重新发起连接")
    }

    /**
     * 用拿到的令牌拉一次账号信息，验证可用，然后：
     * 1. 加密保存令牌；
     * 2. 自动登记 GitHub 官方远程 MCP 服务器（提供写操作能力）；
     * 3. 按开关把同一份凭据同步进 Linux 环境的 git / gh。
     */
    fun completeAuthorization(
        context: Context,
        store: ConnectorStore,
        token: String,
    ): Connected {
        val viewer = GitHubApiClient(token).viewer()
        val login = viewer.optString("login").trim()
        if (login.isBlank()) {
            throw GitHubAuthException("CONNECTOR_AUTH_BAD_RESPONSE", "GitHub 未返回账号信息")
        }
        store.saveToken(GitHubConnector.ID, token)
        val current = store.connection(GitHubConnector.ID)

        val mcpServerId = runCatching {
            kotlinx.coroutines.runBlocking { GitHubMcpRegistrar.register(context, token) }
        }.getOrElse { "" }

        val terminal = if (current.syncToTerminal) {
            GitHubTerminalSync.sync(context, login, token)
        } else {
            GitHubTerminalSync.Result()
        }

        val updated = current.copy(
            connected = true,
            enabled = true,
            account = login,
            avatarUrl = viewer.optString("avatar_url"),
            scopes = GitHubDeviceFlow.SCOPE.split(' ').filter { it.isNotBlank() },
            updatedAt = System.currentTimeMillis(),
            mcpServerId = mcpServerId,
        )
        store.save(updated)
        return Connected(
            connection = updated,
            mcpServerId = mcpServerId,
            terminalSynced = terminal.synced,
            terminalSkipped = terminal.skipped,
            terminalError = terminal.error,
        )
    }

    /** 断开：撤销 MCP 条目、清终端凭据、删本机令牌与状态。 */
    fun disconnect(context: Context) {
        val store = ConnectorStore(context)
        val current = store.connection(GitHubConnector.ID)
        kotlinx.coroutines.runBlocking {
            GitHubMcpRegistrar.unregister(current.mcpServerId)
        }
        runCatching { GitHubTerminalSync.clear(context) }
        store.disconnect(GitHubConnector.ID)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val store = ConnectorStore(context)
        store.save(store.connection(GitHubConnector.ID).copy(enabled = enabled))
    }

    /** 开关「同步给终端 git」；打开时立刻写一次，关闭时清掉已有凭据。 */
    fun setSyncToTerminal(context: Context, enabled: Boolean): GitHubTerminalSync.Result {
        val store = ConnectorStore(context)
        val current = store.connection(GitHubConnector.ID)
        store.save(current.copy(syncToTerminal = enabled))
        if (!current.connected) return GitHubTerminalSync.Result()
        return if (enabled) {
            val token = store.token(GitHubConnector.ID)
            if (token.isNullOrBlank()) {
                GitHubTerminalSync.Result(error = "本机令牌缺失，请重新授权")
            } else {
                GitHubTerminalSync.sync(context, current.account, token)
            }
        } else {
            GitHubTerminalSync.clear(context)
        }
    }

    /** 令牌存疑时重跑一次后置动作，不动令牌本身。 */
    fun resync(context: Context): Connected? {
        val store = ConnectorStore(context)
        val current = store.connection(GitHubConnector.ID)
        val token = store.token(GitHubConnector.ID)
        if (!current.connected || token.isNullOrBlank()) return null
        val mcpServerId = runCatching {
            kotlinx.coroutines.runBlocking { GitHubMcpRegistrar.register(context, token) }
        }.getOrElse { current.mcpServerId }
        val terminal = if (current.syncToTerminal) {
            GitHubTerminalSync.sync(context, current.account, token)
        } else {
            GitHubTerminalSync.Result()
        }
        val updated = current.copy(mcpServerId = mcpServerId)
        store.save(updated)
        return Connected(
            connection = updated,
            mcpServerId = mcpServerId,
            terminalSynced = terminal.synced,
            terminalSkipped = terminal.skipped,
            terminalError = terminal.error,
        )
    }

    /** 未连接时给模型看的提示，让它在对话里引导用户去授权，而不是直接报错。 */
    fun notConnectedHint(): String = JSONObject()
        .put("ok", false)
        .put("code", "CONNECTOR_NOT_CONNECTED")
        .put("message", "GitHub 尚未连接")
        .put("action", "open_connector")
        .put("connectorId", GitHubConnector.ID)
        .toString()
}
