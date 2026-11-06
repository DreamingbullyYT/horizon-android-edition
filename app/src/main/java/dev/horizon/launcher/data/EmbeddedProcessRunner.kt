package dev.horizon.launcher.data

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/** Executes Termux commands in a private PRoot root using Termux's Android linker-exec bridge. */
object EmbeddedProcessRunner {
    data class Result(val exitCode: Int, val output: String, val logFile: File)

    fun runScript(context: Context, script: File, args: List<String> = emptyList()): Result {
        require(script.isFile) { "Build script not found: ${script.name}" }
        val guestScript = guestPath(context, script)
        val guestArgs = args.map { arg ->
            if (arg.startsWith("/")) runCatching { guestPath(context, File(arg)) }.getOrDefault(arg) else arg
        }
        val command = "exec ${EmbeddedTermuxPaths.GUEST_PREFIX}/bin/bash ${shellQuote(guestScript)} " +
            guestArgs.joinToString(" ") { shellQuote(it) }
        return run(context, command, script.name)
    }

    fun runShellCommand(context: Context, command: String, label: String): Result =
        run(context, command, label)

    private fun run(context: Context, shellCommand: String, label: String): Result {
        val root = EmbeddedTermuxPaths.root(context)
        val rootfs = EmbeddedTermuxPaths.rootfs(context)
        val termuxData = EmbeddedTermuxPaths.termuxData(context)
        val prefix = EmbeddedTermuxPaths.prefix(context)
        val proot = EmbeddedTermuxPaths.proot(context)
        val loader = EmbeddedTermuxPaths.loader(context)
        val prootLib = EmbeddedTermuxPaths.prootLib(context)
        val state = EmbeddedTermuxPaths.state(context)
        val home = EmbeddedTermuxPaths.home(context)
        val tmp = EmbeddedTermuxPaths.tmp(context)
        val logs = File(state, "logs")
        listOf(root, rootfs, termuxData, home, tmp, logs, prootLib).forEach {
            require(it.isDirectory || it.mkdirs()) { "Cannot create embedded Termux runtime directories." }
        }
        require(proot.isFile && loader.isFile) { "Embedded PRoot runtime is missing. Reinstall the Horizon app build." }
        val linkerPreload = File(prefix, "lib/libtermux-exec-linker-ld-preload.so")
        require(linkerPreload.isFile) { "Termux Android linker compatibility library is missing." }

        val guestCommand = mutableListOf(
            proot.absolutePath, "-r", "/", "--link2symlink", "--kill-on-exit",
            "-b", "${termuxData.absolutePath}:/data/data/com.termux",
            "-b", "${context.filesDir.absolutePath}:/horizon-host",
            "-w", EmbeddedTermuxPaths.GUEST_HOME,
            "${EmbeddedTermuxPaths.GUEST_PREFIX}/bin/env", "-u", "LD_LIBRARY_PATH",
            "${EmbeddedTermuxPaths.GUEST_PREFIX}/bin/bash", "-lc", shellCommand
        )
        val hostCommand = mutableListOf(
            "/system/bin/sh", "-c", "exec \"\$@\"", "horizon"
        )
        hostCommand.addAll(guestCommand)

        val process = ProcessBuilder(hostCommand).directory(state).redirectErrorStream(true)
        val env = process.environment()
        env["PROOT"] = proot.absolutePath
        env["PROOT_LOADER"] = loader.absolutePath
        env["PROOT_TMP_DIR"] = tmp.absolutePath
        env["PROOT_NO_SECCOMP"] = "1"
        env["LD_LIBRARY_PATH"] = prootLib.absolutePath
        env["LD_PRELOAD"] = linkerPreload.absolutePath
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE"] = "force"
        env["TERMUX_EXEC__SYSTEM_LINKER_EXEC__LOG_LEVEL"] = "1"
        env["PREFIX"] = EmbeddedTermuxPaths.GUEST_PREFIX
        env["TERMUX__PREFIX"] = EmbeddedTermuxPaths.GUEST_PREFIX
        env["TERMUX__USER_ID"] = Process.myUid().toString()
        env["TERMUX_APP__PACKAGE_NAME"] = "com.termux"
        env["TERMUX_APP__DATA_DIR"] = "/data/data/com.termux"
        env["TERMUX__ROOTFS"] = "/data/data/com.termux/files"
        env["TERMUX__NAME"] = "Horizon"
        env["ANDROID__BUILD_VERSION_SDK"] = Build.VERSION.SDK_INT.toString()
        env["HOME"] = EmbeddedTermuxPaths.GUEST_HOME
        env["TMPDIR"] = "${EmbeddedTermuxPaths.GUEST_PREFIX}/tmp"
        env["PATH"] = "/system/bin:/system/xbin"
        env["LANG"] = "en_US.UTF-8"
        env["TERM"] = "xterm-256color"
        env["HORIZON_EMBEDDED"] = "1"

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

    private fun guestPath(context: Context, file: File): String {
        val base = context.filesDir.canonicalFile.toPath()
        val path = file.canonicalFile.toPath()
        require(path.startsWith(base)) { "Build input is outside Horizon's private directory." }
        val relative = base.relativize(path).toString().replace(File.separatorChar, '/')
        return "/horizon-host/$relative"
    }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
