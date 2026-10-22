package com.example.fintrack.data.local

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Minimal persistence abstraction so repositories stay testable (no Android types).
 */
interface LocalStore<T> {
    /** Returns the stored value, or null when nothing (valid) has been stored yet. */
    fun load(): T?

    fun save(value: T)
}

/**
 * Stores a single @Serializable snapshot as a JSON file.
 *
 * Writes are atomic (temp file + rename) so a crash or a killed process can never leave a
 * half-written ledger on disk. A corrupt file is preserved as `<name>.corrupt` instead of
 * being silently overwritten, so the user's data can still be recovered manually.
 */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val json: Json = DefaultJson
) : LocalStore<T> {

    @Synchronized
    override fun load(): T? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            runCatching { file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true) }
            null
        }
    }

    @Synchronized
    override fun save(value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
        try {
            Files.move(
                tmp.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }
}
