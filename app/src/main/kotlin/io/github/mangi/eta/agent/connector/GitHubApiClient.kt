package io.github.mangi.eta.agent.connector

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal class GitHubApiException(
    val code: String,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * GitHub REST 只读客户端，固定 host 为 api.github.com，拒绝重定向到其他主机。
 * 一期只实现四个只读能力：仓库列表、仓库详情、Issue 列表、文件读取。
 */
internal class GitHubApiClient(private val token: String) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 取当前账号信息，授权成功后用来展示用户名和头像。 */
    fun viewer(): JSONObject = get(API_BASE.toHttpUrl().newBuilder().addPathSegment("user").build())

    fun listRepositories(limit: Int, visibility: String?): JSONArray {
        val url = api("user", "repos").newBuilder()
            .addQueryParameter("sort", "updated")
            .addQueryParameter("direction", "desc")
            .addQueryParameter("per_page", limit.coerceIn(1, 100).toString())
            .apply {
                visibility?.takeIf { it.isNotBlank() }?.let { addQueryParameter("visibility", it) }
            }
            .build()
        return getArray(url)
    }

    fun getRepository(owner: String, repo: String): JSONObject =
        get(api("repos", owner, repo))

    fun listIssues(owner: String, repo: String, state: String, limit: Int): JSONArray {
        val url = api("repos", owner, repo, "issues").newBuilder()
            .addQueryParameter("state", state.ifBlank { "open" })
            .addQueryParameter("per_page", limit.coerceIn(1, 100).toString())
            .build()
        return getArray(url)
    }

    fun readFile(owner: String, repo: String, path: String, ref: String?): JSONObject {
        val url = api("repos", owner, repo, "contents", *path.split('/').filter { it.isNotBlank() }.toTypedArray())
            .newBuilder()
            .apply { ref?.takeIf { it.isNotBlank() }?.let { addQueryParameter("ref", it) } }
            .build()
        return get(url)
    }

    private fun api(vararg segments: String): HttpUrl =
        segments.fold(API_BASE.toHttpUrl().newBuilder()) { builder, segment ->
            builder.addPathSegment(segment)
        }.build()

    private fun getArray(url: HttpUrl): JSONArray {
        val text = execute(url)
        return runCatching { JSONArray(text) }.getOrElse {
            throw GitHubApiException("GITHUB_BAD_RESPONSE", "GitHub 返回内容不是 JSON 数组")
        }
    }

    private fun get(url: HttpUrl): JSONObject {
        val text = execute(url)
        return runCatching { JSONObject(text) }.getOrElse {
            throw GitHubApiException("GITHUB_BAD_RESPONSE", "GitHub 返回内容不是 JSON 对象")
        }
    }

    private fun execute(url: HttpUrl): String {
        if (url.host != API_HOST) {
            throw GitHubApiException("GITHUB_HOST_REJECTED", "拒绝访问非 GitHub API 主机")
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isRedirect -> throw GitHubApiException(
                        "GITHUB_REDIRECT_REJECTED",
                        "GitHub 接口发生重定向，已拒绝跟随",
                    )
                    response.code == 401 -> throw GitHubApiException(
                        "CONNECTOR_REAUTH_REQUIRED",
                        "GitHub 授权已失效，请重新授权",
                    )
                    response.code == 403 || response.code == 429 -> throw GitHubApiException(
                        "GITHUB_RATE_LIMITED",
                        "GitHub 请求受限，请稍后重试",
                    )
                    response.code == 404 -> throw GitHubApiException(
                        "GITHUB_NOT_FOUND",
                        "未找到对应的仓库、Issue 或文件",
                    )
                    !response.isSuccessful -> throw GitHubApiException(
                        "GITHUB_REQUEST_FAILED",
                        "GitHub 请求失败（HTTP ${response.code}）",
                    )
                }
                response.body.string()
            }
        } catch (failure: GitHubApiException) {
            throw failure
        } catch (failure: IOException) {
            throw GitHubApiException("GITHUB_NETWORK_ERROR", "无法连接 GitHub，请检查网络", failure)
        }
    }

    private companion object {
        /** 只用于主机校验，不带 scheme。 */
        const val API_HOST = "api.github.com"

        /** 建 URL 必须带 scheme，String.toHttpUrl() 不接受裸域名。 */
        const val API_BASE = "https://api.github.com"
        const val USER_AGENT = "Eta-Android"
        const val API_VERSION = "2022-11-28"
    }
}
