package net.joshe.signman.client

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories

fun writeFile(file: File, permissions: String = "rw-rw----", body: (OutputStream) -> Unit) {
    val dir = file.toPath().normalize().parent
    dir.createDirectories()
    val tmp = Files.createTempFile(dir, ".tmp", ".json",
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions)))
        .toFile()
    tmp.outputStream().use(body)
    tmp.renameTo(file)
}

fun getUserStateDir(subdirectory: String) = System.getenv("XDG_STATE_HOME")?.let { state ->
        File(state, subdirectory)
} ?: File(File(System.getProperty("user.home")!!, ".config"), subdirectory)
