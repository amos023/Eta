package io.github.mangi.eta.agent.terminal

import java.io.File

/** 普通模式独立于旧版由 Root 创建的 terminal 父目录，不修改 chroot 的路径和属主。 */
internal object TerminalPrivateStorage {
    fun workspace(filesDir: File): File = directory(filesDir, "workspace")

    /**
     * 每个会话一个稳定、可读的独立工作区。
     * 首次访问时用会话标题生成目录名并写入索引；以后即使会话改名，路径也不变。
     */
    @Synchronized
    fun conversationWorkspace(filesDir: File, conversationId: String, title: String = ""): File {
        val conversationsRoot = File(directory(filesDir, "conversations"), "projects").apply { mkdirs() }
        val safeId = conversationId
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(128)
            .ifBlank { "draft" }
        val indexFile = File(conversationsRoot.parentFile, ".workspace-index.json")
        val index = runCatching {
            if (indexFile.isFile) org.json.JSONObject(indexFile.readText(Charsets.UTF_8)) else org.json.JSONObject()
        }.getOrElse { org.json.JSONObject() }
        val existing = index.optString(safeId).takeIf { it.isNotBlank() }
        val directoryName = existing ?: "${safeDirectoryTitle(title)}--$safeId".also { name ->
            index.put(safeId, name)
            val atomic = android.util.AtomicFile(indexFile)
            val output = atomic.startWrite()
            try {
                output.write(index.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (failure: Throwable) {
                atomic.failWrite(output)
                throw failure
            }
        }
        return File(conversationsRoot, directoryName).apply { mkdirs() }
    }

    private fun safeDirectoryTitle(title: String): String = title.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim('.', ' ')
        .take(48)
        .ifBlank { "会话" }

    fun prootEnvironment(filesDir: File, distribution: LinuxDistribution): File =
        directory(filesDir, "proot/${distribution.wireName}")

    private fun directory(filesDir: File, relative: String): File {
        val independent = File(filesDir, "terminal-user/$relative")
        val legacy = File(filesDir, "terminal/$relative")
        // 已有普通环境继续使用原位置；路径选择不读取 Root 授权，也不搬动已有数据。
        return if (!independent.exists() && legacy.exists()) legacy else independent
    }

    fun isProotPath(path: String?): Boolean =
        path?.let { "/terminal-user/proot/" in it || "/terminal/proot/" in it } == true
}
