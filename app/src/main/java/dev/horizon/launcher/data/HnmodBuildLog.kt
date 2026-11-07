package dev.horizon.launcher.data

import android.content.Context
import java.io.File

/** Shared append-only log shown by Horizon while the embedded HNMod compiler is running. */
object HnmodBuildLog {
    private const val FILE_NAME = "hnmod-live.log"

    fun file(context: Context): File =
        File(EmbeddedTermuxPaths.state(context), "logs/$FILE_NAME")

    @Synchronized
    fun begin(context: Context) {
        val target = file(context)
        require(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
            "Cannot create HNMod live log directory."
        }
        target.writeText("Horizon HNMod compiler log\n")
    }

    @Synchronized
    fun append(context: Context, line: String) {
        val target = file(context)
        require(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
            "Cannot create HNMod live log directory."
        }
        target.appendText(line + "\n")
    }
}
