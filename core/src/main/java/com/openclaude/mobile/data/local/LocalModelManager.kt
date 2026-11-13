package com.openclaude.mobile.data.local

import android.content.Context
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Where local models live and which one is selected.
 *
 * A "model" is a single GGUF file plus a small sidecar of facts we learned when it
 * was installed (quantisation, size, whether it was verified). The sidecar means
 * the picker never has to open a 2 GB file to read its header.
 */
class LocalModelManager(private val context: Context) {

    @Serializable
    data class ModelRecord(
        val id: String,
        val label: String,
        val file: String,
        val sizeBytes: Long,
        val quant: String,
        val contextTrain: Int,
        val installedAt: Long,
        val verified: Boolean
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _installed = MutableStateFlow<List<ModelRecord>>(emptyList())
    val installed: StateFlow<List<ModelRecord>> = _installed

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    private fun indexPath(): File = File(modelsDir(), "models.json")

    fun load() {
        val records = runCatching {
            if (!indexPath().exists()) emptyList()
            else json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ModelRecord.serializer()),
                indexPath().readText()
            )
        }.getOrElse {
            Logs.w("models", "model index unreadable, rebuilding from disk")
            rebuildFromDisk()
        }

        // Drop records whose file disappeared (user cleared storage, failed copy).
        val present = records.filter { File(modelsDir(), it.file).exists() }
        _installed.value = present
        _activeId.value = present.firstOrNull()?.id
        if (present.size != records.size) save(present)
    }

    private fun rebuildFromDisk(): List<ModelRecord> = modelsDir().listFiles()
        ?.filter { it.name.endsWith(".gguf") }
        ?.map { file ->
            ModelRecord(
                id = file.nameWithoutExtension,
                label = file.nameWithoutExtension,
                file = file.name,
                sizeBytes = file.length(),
                quant = inferQuant(file.name),
                contextTrain = 4096,
                installedAt = file.lastModified(),
                verified = false
            )
        } ?: emptyList()

    private fun inferQuant(name: String): String {
        val match = Regex("(?i)(q\\d[_a-z]*\\d*|f16|f32)").find(name)
        return match?.value?.uppercase() ?: "unknown"
    }

    fun save(records: List<ModelRecord>) {
        indexPath().writeText(
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ModelRecord.serializer()),
                records
            )
        )
        _installed.value = records
    }

    fun installedModel(): ModelRecord? =
        _installed.value.firstOrNull { it.id == _activeId.value } ?: _installed.value.firstOrNull()

    fun select(id: String) {
        _activeId.value = id
        Logs.i("models", "active model -> $id")
    }

    /** Copies a user-picked GGUF into app storage so it survives a restart. */
    suspend fun importFrom(uri: android.net.Uri, displayName: String): Result<ModelRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(modelsDir(), displayName.substringAfterLast('/'))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out, 1 shl 20) }
                } ?: throw IllegalStateException("cannot open $uri")

                val record = ModelRecord(
                    id = target.nameWithoutExtension,
                    label = target.nameWithoutExtension,
                    file = target.name,
                    sizeBytes = target.length(),
                    quant = inferQuant(target.name),
                    contextTrain = 4096,
                    installedAt = System.currentTimeMillis(),
                    verified = false
                )
                val updated = _installed.value.filterNot { it.id == record.id } + record
                save(updated)
                _activeId.value = record.id
                Logs.i("models", "imported ${record.id} (${record.sizeBytes} bytes)")
                record
            }
        }

    fun delete(id: String) {
        val record = _installed.value.firstOrNull { it.id == id } ?: return
        File(modelsDir(), record.file).delete()
        val updated = _installed.value.filterNot { it.id == id }
        save(updated)
        if (_activeId.value == id) _activeId.value = updated.firstOrNull()?.id
    }

    fun totalBytes(): Long = _installed.value.sumOf { it.sizeBytes }
}
