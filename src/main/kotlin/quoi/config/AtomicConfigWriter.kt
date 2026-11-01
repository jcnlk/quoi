package quoi.config

import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeConfigAtomically(file: File, write: (Writer) -> Unit) {
    val temporary = Files.createTempFile(file.absoluteFile.parentFile.toPath(), "${file.name}.", ".tmp")
    try {
        temporary.toFile().bufferedWriter().use(write)
        Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        Files.deleteIfExists(temporary)
    }
}
