package dev.horizon.launcher.data

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/**
 * Starts Horizon-prefix binaries through the system shell with Termux's linker-exec preload.
 *
 * Direct execve() of writable app-private binaries is not relied upon. The system shell loads the
 * pinned linker preload, which redirects child execution through Android's system linker.
 */
object EmbeddedProcessRunner {
    data class Result(val exitCode: Int, val output: String, val logFile: File)

    fun runScript(context: Context, prefix: File, script: File, args: List<String> = emptyList()): Result {
        require(script.isFile) { "Toolchain script not found: ${script.name}" }
        val command = mutableListOf(
            "/system/bin/sh",
            "-c",
            "exec \"\$PREFIX/bin/bash\" \"\$@\"",
            "horizon",
            script.absolutePath
        )
        command.addAll(args)
        return run(context, prefix, command, script.name)
    }

    fun runShellCommand(context: Context, prefix: File, command: String, label: String): Result {
        val argv = listOf(
            "/system/bin/sh",
            "-c",
            "exec \"\$PREFIX/bin/bash\" -c \"\$1\"",
            "horizon",
            command
        )
        return run(context, prefix, argv, label)
    }

    private fun run(context: Context, prefix: File, command: List<String>, label: String): Result {
        val state = File(context.filesDir, "horizon-toolchain")
        val home = File(state, "home")
        val tmp = File(state, "tmp")
        val logs = File(state, "logs")
        listOf(state, home, tmp, logs).forEach {
            require(it.isDirectory || it.mkdirs()) { "Cannot create Horizon toolchain directory." }
        }

        val linkerPreload = File(prefix, "lib/libtermux-exec-linker-ld-preload.so")
        require(linkerPreload.isFile) { "Android linker compatibility library is missing from the bootstrap." }

        val prefixAlias = "/data/data/${context.packageName}/files/usr"
        val appDataAlias = "/data/data/${context.packageName}"
        val process = ProcessBuilder(command)
            .directory(state)
            .redirectErrorStream(true)

        val env = process.environment()
        env["HOME"] = home.absolutePath
        env["TMPDIR"] = tmp.absolutePath
        env["PREFIX"] = prefixAlias
        env["TERMUX__PREFIX"] = prefixAlias
        env["TERMUX__USER_ID"] = Process.myUid().toString()
        env["TERMUX_APP__PACKAGE_NAME"] = context.packageName
        env["TERMUX_APP__DATA_DIR"] = appDataAlias
        env["TERMUX__ROOTFS"] = "$appDataAlias/files"
        env["TERMUX__NAME"] = "Horizon"
        env["TERMUX_APP__NAME"] = "Horizon"
        env["ANDROID__BUILD_VERSION_SDK"] = Build.VERSION.SDK_INT.toString()
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE"] = "force"
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__LOG_LEVEL"] = "1"
        env["LD_PRELOAD"] = linkerPreload.absolutePath
        env["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
        env["PATH"] = listOf(
            File(prefix, "bin").absolutePath,
            "/system/bin",
            "/system/xbin"
        ).joinToString(File.pathSeparator)
        env["SHELL"] = File(prefix, "bin/bash").absolutePath
        env["LANG"] = "C.UTF-8"
        env["LC_ALL"] = "C.UTF-8"
        env["TERM"] = "xterm-256color"

        val logFile = File(logs, "${label.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.log")
        val output = StringBuilder()
        val child = process.start()
        logFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            child.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    writer.appendLine(line)
                    output.appendLine(line)
                    if (output.length > 256 * 1024) output.delete(0, output.length - 192 * 1024)
                }
            }
        }
        val exitCode = child.waitFor()
        return Result(exitCode, output.toString(), logFile)
    }
}
