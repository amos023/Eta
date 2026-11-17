package io.github.mangi.eta.agent.connector

import android.content.Context
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import java.util.Base64

/**
 * 把 GitHub 凭据同步进 Linux 环境，让终端里的 git / gh 直接用同一个账号。
 *
 * 写两个位置，按需：
 *  - `~/.git-credentials` + `.gitconfig` 的 `credential.helper = store`：
 *    不依赖 gh 也能 clone / push，这是主力通道。
 *  - `~/.config/gh/hosts.yml`：装了 gh 时让 gh 命令与 `gh auth git-credential` 一并可用。
 *
 * PRoot 后端下 rootfs 里的 `/root` 归 App 自己的 uid，可以直接写；
 * CHROOT 后端下 `/root` 归 root（700），App 写不进去，只能拼固定脚本交给 su。
 * 两条路径都只由本文件的常量与 base64 编码后的凭据组成，不含用户自由输入。
 */
internal object GitHubTerminalSync {
    data class Result(
        val synced: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val ok: Boolean get() = error == null && synced.isNotEmpty()
    }

    fun sync(context: Context, login: String, token: String): Result {
        val ready = readyEnvironments(context)
        if (ready.isEmpty()) {
            return Result(skipped = LinuxDistribution.entries.map { it.wireName })
        }
        val credentials = "https://x-access-token:$token@github.com\n"
        val hosts = buildString {
            appendLine("github.com:")
            appendLine("    user: $login")
            appendLine("    oauth_token: $token")
            appendLine("    git_protocol: https")
        }
        val synced = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var error: String? = null
        ready.forEach { (distribution, rootfs) ->
            val home = File(rootfs, "root")
            val written = runCatching { writeDirect(home, login, credentials, hosts, rootfs) }
                .getOrElse { writeViaRoot(home, login, credentials, hosts, rootfs) }
            if (written) synced += distribution.wireName else {
                failed += distribution.wireName
                if (error == null) error = "写入 ${distribution.wireName} 失败"
            }
        }
        return Result(synced = synced, skipped = failed, error = error)
    }

    fun clear(context: Context): Result {
        val ready = readyEnvironments(context)
        if (ready.isEmpty()) return Result(skipped = LinuxDistribution.entries.map { it.wireName })
        val synced = mutableListOf<String>()
        var error: String? = null
        ready.forEach { (distribution, rootfs) ->
            val home = File(rootfs, "root")
            val cleared = runCatching {
                File(home, ".git-credentials").delete()
                File(home, ".config/gh/hosts.yml").delete()
                true
            }.getOrElse {
                removeViaRoot(home)
            }
            if (cleared) synced += distribution.wireName else {
                if (error == null) error = "清除 ${distribution.wireName} 失败"
            }
        }
        return Result(synced = synced, error = error)
    }

    /** PRoot 后端：/root 属于 App uid，直接落盘；写完顺手收紧权限。 */
    private fun writeDirect(
        home: File,
        login: String,
        credentials: String,
        hosts: String,
        rootfs: File,
    ): Boolean {
        if (!home.isDirectory && !home.mkdirs()) return false
        val credentialFile = File(home, ".git-credentials")
        credentialFile.writeText(credentials, Charsets.UTF_8)
        lockDown(credentialFile)

        val gitConfig = File(home, ".gitconfig")
        val existing = if (gitConfig.isFile) gitConfig.readText(Charsets.UTF_8) else ""
        val merged = buildString {
            append(existing)
            if (!existing.contains("[credential \"https://github.com\"]")) {
                append("\n[credential \"https://github.com\"]\n\thelper = store\n")
            }
            if (!EMAIL_LINE.containsMatchIn(existing)) {
                append("\n[user]\n\tname = $login\n\temail = $login@users.noreply.github.com\n")
            }
        }
        gitConfig.writeText(merged, Charsets.UTF_8)
        lockDown(gitConfig)

        if (hasGh(rootfs)) {
            val ghDir = File(home, ".config/gh")
            if (!ghDir.isDirectory) ghDir.mkdirs()
            val hostsFile = File(ghDir, "hosts.yml")
            hostsFile.writeText(hosts, Charsets.UTF_8)
            lockDown(hostsFile)
        }
        return credentialFile.isFile
    }

    /** CHROOT 后端：交给 su 执行固定脚本，脚本内容全部来自本文件常量与 base64 凭据。 */
    private fun writeViaRoot(
        home: File,
        login: String,
        credentials: String,
        hosts: String,
        rootfs: File,
    ): Boolean {
        val encodedCredentials = encode(credentials)
        val encodedHosts = encode(hosts)
        val homePath = home.absolutePath
        val gitConfig = "$homePath/.gitconfig"
        val script = buildString {
            append("mkdir -p '$homePath/.config/gh'\n")
            append("printf %s '$encodedCredentials' | base64 -d > '$homePath/.git-credentials'\n")
            append("chmod 600 '$homePath/.git-credentials'\n")
            append("[ -f '$gitConfig' ] || : > '$gitConfig'\n")
            append("grep -q 'credential \"https://github.com\"' '$gitConfig' 2>/dev/null || ")
            append("printf '\\n[credential \"https://github.com\"]\\n\\thelper = store\\n' >> '$gitConfig'\n")
            append("grep -q '^[[:space:]]*email' '$gitConfig' 2>/dev/null || ")
            append("printf '\\n[user]\\n\\tname = $login\\n\\temail = ${login}@users.noreply.github.com\\n' >> '$gitConfig'\n")
            append("chmod 600 '$gitConfig'\n")
            append("if [ -x '$rootfs/usr/bin/gh' ] || [ -x '$rootfs/usr/local/bin/gh' ]; then ")
            append("printf %s '$encodedHosts' | base64 -d > '$homePath/.config/gh/hosts.yml'; ")
            append("chmod 600 '$homePath/.config/gh/hosts.yml'; fi\n")
        }
        return runCatching {
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            val finished = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }

    private fun removeViaRoot(home: File): Boolean = runCatching {
        val script = "rm -f '${home.absolutePath}/.git-credentials' '${home.absolutePath}/.config/gh/hosts.yml'\n"
        val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        val finished = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    }.getOrDefault(false)

    private fun lockDown(file: File) {
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
        AndroidAgentLogger.debug { "connector credential written: ${file.name}" }
    }

    private fun readyEnvironments(context: Context): List<Pair<LinuxDistribution, File>> =
        LinuxDistribution.entries.mapNotNull { distribution ->
            val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution)
            if (LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) distribution to rootfs else null
        }

    private fun hasGh(rootfs: File): Boolean =
        File(rootfs, "usr/bin/gh").exists() || File(rootfs, "usr/local/bin/gh").exists()

    private fun encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private val EMAIL_LINE = Regex("^[ \\t]*email", RegexOption.MULTILINE)
}
