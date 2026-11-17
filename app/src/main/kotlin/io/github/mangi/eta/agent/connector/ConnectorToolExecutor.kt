package io.github.mangi.eta.agent.connector

import android.util.Base64
import io.github.mangi.eta.agent.model.AgentModelClient
import org.json.JSONArray
import org.json.JSONObject

/** 承载一次 run 的连接器工具调用；快照在 run 开始时冻结，授权状态变化从下一次 run 生效。 */
internal class ConnectorToolExecutor(
    private val store: ConnectorStore,
) : AgentModelClient.ToolExecutor, AutoCloseable {

    override fun execute(toolCall: AgentModelClient.ToolCall): AgentModelClient.ToolResult {
        val name = toolCall.name
        if (name !in ConnectorRegistry.allToolNames()) {
            return failure("UNKNOWN_CONNECTOR_TOOL", "连接器工具未注册")
        }
        if (!io.github.mangi.eta.config.Prefs.isToolEnabled(name)) {
            return failure("TOOL_DISABLED", "工具 $name 已在工具列表中关闭，本次未执行")
        }
        val connection = store.connection(GitHubConnector.ID)
        if (!connection.enabled) {
            return failure("CONNECTOR_DISABLED", "GitHub 连接器已停用，请在 设置 → 连接器 中开启")
        }
        if (!connection.connected) {
            return failure(
                code = "CONNECTOR_NOT_CONNECTED",
                message = "GitHub 尚未连接，请先在 设置 → 连接器 → GitHub 完成授权",
                action = "open_connector",
            )
        }
        val token = store.token(GitHubConnector.ID)
        if (token.isNullOrBlank()) {
            return failure(
                code = "CONNECTOR_REAUTH_REQUIRED",
                message = "GitHub 授权凭据已失效，请重新授权",
                action = "open_connector",
            )
        }
        val arguments = runCatching { JSONObject(toolCall.argumentsJson.ifBlank { "{}" }) }
            .getOrElse { return failure("INVALID_ARGUMENT", "工具参数不是 JSON object") }
        return runCatching {
            val client = GitHubApiClient(token)
            when (name) {
                GitHubConnector.TOOL_LIST_REPOSITORIES -> listRepositories(client, arguments)
                GitHubConnector.TOOL_GET_REPOSITORY -> getRepository(client, arguments)
                GitHubConnector.TOOL_LIST_ISSUES -> listIssues(client, arguments)
                GitHubConnector.TOOL_READ_FILE -> readFile(client, arguments)
                else -> failure("UNKNOWN_CONNECTOR_TOOL", "连接器工具未注册")
            }
        }.getOrElse { throwable ->
            when (throwable) {
                is GitHubApiException -> failure(throwable.code, throwable.message ?: "GitHub 请求失败")
                else -> failure("CONNECTOR_CALL_FAILED", "GitHub 工具调用失败")
            }
        }
    }

    override fun close() = Unit

    private fun listRepositories(
        client: GitHubApiClient,
        arguments: JSONObject,
    ): AgentModelClient.ToolResult {
        val limit = arguments.optInt("limit", 20).coerceIn(1, 100)
        val visibility = arguments.optString("visibility").takeIf { it.isNotBlank() }
        val raw = client.listRepositories(limit, visibility)
        val repositories = JSONArray()
        for (index in 0 until raw.length()) {
            val repo = raw.optJSONObject(index) ?: continue
            repositories.put(repositorySummary(repo))
        }
        return success(
            JSONObject()
                .put("ok", true)
                .put("count", repositories.length())
                .put("repositories", repositories),
        )
    }

    private fun getRepository(
        client: GitHubApiClient,
        arguments: JSONObject,
    ): AgentModelClient.ToolResult {
        val owner = arguments.optString("owner").trim()
        val repo = arguments.optString("repo").trim()
        if (owner.isBlank() || repo.isBlank()) {
            return failure("INVALID_ARGUMENT", "需要同时提供 owner 与 repo")
        }
        val detail = client.getRepository(owner, repo)
        return success(
            JSONObject()
                .put("ok", true)
                .put("repository", repositorySummary(detail).apply {
                    put("default_branch", detail.optString("default_branch"))
                    put("topics", detail.optJSONArray("topics") ?: JSONArray())
                    put("open_issues", detail.optInt("open_issues_count"))
                }),
        )
    }

    private fun listIssues(
        client: GitHubApiClient,
        arguments: JSONObject,
    ): AgentModelClient.ToolResult {
        val owner = arguments.optString("owner").trim()
        val repo = arguments.optString("repo").trim()
        if (owner.isBlank() || repo.isBlank()) {
            return failure("INVALID_ARGUMENT", "需要同时提供 owner 与 repo")
        }
        val state = arguments.optString("state").ifBlank { "open" }
        val limit = arguments.optInt("limit", 20).coerceIn(1, 100)
        val raw = client.listIssues(owner, repo, state, limit)
        val issues = JSONArray()
        for (index in 0 until raw.length()) {
            val issue = raw.optJSONObject(index) ?: continue
            // Pull Request 也会出现在 issues 接口里，这里过滤掉。
            if (issue.has("pull_request")) continue
            issues.put(
                JSONObject()
                    .put("number", issue.optInt("number"))
                    .put("title", issue.optString("title"))
                    .put("state", issue.optString("state"))
                    .put("user", issue.optJSONObject("user")?.optString("login").orEmpty())
                    .put("comments", issue.optInt("comments"))
                    .put("created_at", issue.optString("created_at"))
                    .put("updated_at", issue.optString("updated_at"))
                    .put("labels", JSONArray().also { labels ->
                        issue.optJSONArray("labels")?.let { raw_labels ->
                            for (labelIndex in 0 until raw_labels.length()) {
                                val label = raw_labels.optJSONObject(labelIndex) ?: continue
                                labels.put(label.optString("name"))
                            }
                        }
                    }),
            )
        }
        return success(
            JSONObject()
                .put("ok", true)
                .put("repository", "$owner/$repo")
                .put("count", issues.length())
                .put("issues", issues),
        )
    }

    private fun readFile(
        client: GitHubApiClient,
        arguments: JSONObject,
    ): AgentModelClient.ToolResult {
        val owner = arguments.optString("owner").trim()
        val repo = arguments.optString("repo").trim()
        val path = arguments.optString("path").trim()
        if (owner.isBlank() || repo.isBlank() || path.isBlank()) {
            return failure("INVALID_ARGUMENT", "需要提供 owner、repo 与 path")
        }
        val ref = arguments.optString("ref").takeIf { it.isNotBlank() }
        val payload = client.readFile(owner, repo, path, ref)
        if (payload.optString("type") != "file") {
            return failure("GITHUB_NOT_A_FILE", "该路径不是普通文件")
        }
        val encoding = payload.optString("encoding")
        if (encoding != "base64") {
            return failure("GITHUB_UNSUPPORTED_ENCODING", "GitHub 返回了不支持的编码：$encoding")
        }
        val encodedContent = payload.optString("content").replace("\n", "").replace("\r", "")
        val decoded = runCatching { Base64.decode(encodedContent, Base64.DEFAULT) }.getOrNull()
            ?: return failure("GITHUB_BAD_CONTENT", "无法解码文件内容")
        val text = runCatching { decoded.toString(Charsets.UTF_8) }.getOrNull()
            ?: return failure("GITHUB_BAD_CONTENT", "文件内容不是 UTF-8 文本")
        val truncated = text.length > MAX_FILE_CHARS
        return success(
            JSONObject()
                .put("ok", true)
                .put("path", payload.optString("path", path))
                .put("size", payload.optInt("size"))
                .put("sha", payload.optString("sha"))
                .put("truncated", truncated)
                .put("content", if (truncated) text.take(MAX_FILE_CHARS) else text),
        )
    }

    private fun repositorySummary(repo: JSONObject): JSONObject = JSONObject()
        .put("full_name", repo.optString("full_name"))
        .put("private", repo.optBoolean("private"))
        .put("description", repo.optString("description"))
        .put("language", repo.optString("language"))
        .put("stars", repo.optInt("stargazers_count"))
        .put("forks", repo.optInt("forks_count"))
        .put("updated_at", repo.optString("updated_at"))
        .put("html_url", repo.optString("html_url"))

    private fun success(payload: JSONObject): AgentModelClient.ToolResult =
        AgentModelClient.ToolResult(content = payload.toString(), sensitive = true)

    private fun failure(code: String, message: String, action: String? = null): AgentModelClient.ToolResult =
        AgentModelClient.ToolResult(
            content = JSONObject()
                .put("ok", false)
                .put("code", code)
                .put("message", message)
                .apply { if (action != null) put("action", action) }
                .put("connectorId", GitHubConnector.ID)
                .toString(),
            sensitive = true,
        )

    private companion object {
        const val MAX_FILE_CHARS = 60_000
    }
}
