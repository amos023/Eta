package io.github.mangi.eta.agent.connector

import org.json.JSONArray
import org.json.JSONObject

/** GitHub 连接器定义与四个只读工具的 schema。 */
internal object GitHubConnector {
    const val ID = "github"

    const val TOOL_LIST_REPOSITORIES = "github_list_my_repositories"
    const val TOOL_GET_REPOSITORY = "github_get_repository"
    const val TOOL_LIST_ISSUES = "github_list_issues"
    const val TOOL_READ_FILE = "github_read_file"

    val toolNames: Set<String> = setOf(
        TOOL_LIST_REPOSITORIES,
        TOOL_GET_REPOSITORY,
        TOOL_LIST_ISSUES,
        TOOL_READ_FILE,
    )

    val definition: ConnectorDefinition = ConnectorDefinition(
        id = ID,
        kind = ConnectorKind.OAUTH_API,
        displayName = "GitHub",
        description = "读取你自己的仓库、Issue 与文件内容；只读，不会修改任何数据。",
        readOnly = true,
        tools = listOf(
            ConnectorToolDefinition(
                name = TOOL_LIST_REPOSITORIES,
                description = "列出当前 GitHub 账号的仓库，按最近更新排序。用于回答「我有哪些仓库」这类问题。",
                parametersJson = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("limit", JSONObject().put("type", "integer").put("description", "最多返回 1 到 100 个仓库，默认 20"))
                            .put("visibility", JSONObject().put("type", "string").put("enum", JSONArray().put("all").put("public").put("private")).put("description", "可见性筛选，默认 all")),
                    )
                    .toString(),
            ),
            ConnectorToolDefinition(
                name = TOOL_GET_REPOSITORY,
                description = "读取单个仓库的详情：描述、语言、Star、Fork、默认分支、更新时间。",
                parametersJson = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("owner", JSONObject().put("type", "string").put("description", "仓库所有者，例如 octocat"))
                            .put("repo", JSONObject().put("type", "string").put("description", "仓库名，例如 hello-world")),
                    )
                    .put("required", JSONArray().put("owner").put("repo"))
                    .toString(),
            ),
            ConnectorToolDefinition(
                name = TOOL_LIST_ISSUES,
                description = "列出一个仓库的 Issue（不含 Pull Request）。",
                parametersJson = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("owner", JSONObject().put("type", "string").put("description", "仓库所有者"))
                            .put("repo", JSONObject().put("type", "string").put("description", "仓库名"))
                            .put("state", JSONObject().put("type", "string").put("enum", JSONArray().put("open").put("closed").put("all")).put("description", "Issue 状态，默认 open"))
                            .put("limit", JSONObject().put("type", "integer").put("description", "最多返回 1 到 100 条，默认 20")),
                    )
                    .put("required", JSONArray().put("owner").put("repo"))
                    .toString(),
            ),
            ConnectorToolDefinition(
                name = TOOL_READ_FILE,
                description = "读取仓库里的一个文本文件内容；大文件会被截断并标记。",
                parametersJson = JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("owner", JSONObject().put("type", "string").put("description", "仓库所有者"))
                            .put("repo", JSONObject().put("type", "string").put("description", "仓库名"))
                            .put("path", JSONObject().put("type", "string").put("description", "文件路径，例如 README.md"))
                            .put("ref", JSONObject().put("type", "string").put("description", "可选，分支、标签或 commit SHA")),
                    )
                    .put("required", JSONArray().put("owner").put("repo").put("path"))
                    .toString(),
            ),
        ),
    )

    /** 把工具 schema 挂到模型可见的工具列表里。 */
    fun appendToolSchemas(destination: JSONArray) {
        definition.tools.forEach { tool ->
            destination.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("parameters", JSONObject(tool.parametersJson)),
                    ),
            )
        }
    }
}
