package id.kasir.pos

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val MAX_BACKUP_BYTES = 6 * 1024 * 1024
private const val MAX_EXTRACTED_BYTES = 64L * 1024 * 1024
private const val SNAPSHOT_NAME = "data.db"
private const val PROFILE_NAME = "store.json"
private const val FORMAT = 1
private val mediaName = Regex("(logo|qris)-[a-f0-9-]{36}\\.png")

internal data class BackupProfile(val store: StoreProfile, val qris: String) {
    fun settings() = StoreSettings(name = store.name, address = store.address, logoName = store.logoName, qrisImage = qris)
}

internal fun StoreSettings.backupProfile() = BackupProfile(StoreProfile(name, address, logoName), qrisImage)

internal class BackupException(message: String) : IOException(message)

internal class BackupArchive(private val context: Context, private val database: PosDatabase) {
    suspend fun create(destination: File, profile: BackupProfile) {
        val snapshot = File.createTempFile("snapshot-", ".db", context.cacheDir)
        try {
            val media = database.withTransaction {
                val live = database.openHelper.writableDatabase
                SQLiteDatabase.openOrCreateDatabase(snapshot, null).use { copy ->
                    copy.beginTransaction()
                    try {
                        live.query(
                            "SELECT sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                                "AND name != 'android_metadata' ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END"
                        ).use { schema ->
                            while (schema.moveToNext()) copy.execSQL(schema.getString(0))
                        }
                        for (table in live.businessTables() + "room_master_table" + live.sequenceTable()) {
                            if (table == "CartItem" || table == "StoreSettings") continue
                            val sqlTable = identifier(table)
                            live.query("SELECT * FROM $sqlTable").use { rows ->
                                if (table == "sqlite_sequence") copy.execSQL("DELETE FROM $sqlTable")
                                val insert = insertSql(table, rows.columnNames)
                                while (rows.moveToNext()) {
                                    copy.execSQL(insert, Array(rows.columnCount) { rows.getValue(it) })
                                }
                            }
                        }
                        copy.version = live.version
                        copy.setTransactionSuccessful()
                    } finally {
                        copy.endTransaction()
                    }
                }
                buildSet {
                    add(profile.store.logoName)
                    add(profile.qris)
                    live.query("SELECT DISTINCT logoName FROM Sale WHERE logoName != ''").use { logos ->
                        while (logos.moveToNext()) add(logos.getString(0))
                    }
                }.filter { mediaName.matches(it) }.map { File(context.filesDir, it) }.filter { it.isFile }
            }
            if (listOf(profile.store.logoName, profile.qris).any { name ->
                    name.isNotEmpty() && media.none { it.name == name }
                }) throw BackupException("File logo atau QRIS tidak ditemukan. Simpan ulang gambar sebelum mencadangkan.")
            if ((media + snapshot).sumOf { it.length() } > MAX_EXTRACTED_BYTES - 4096) tooLarge()
            ZipOutputStream(LimitedOutput(destination.outputStream())).use { zip ->
                zip.entry(PROFILE_NAME) { write(profile.toJson().toString().toByteArray(Charsets.UTF_8)) }
                for (file in listOf(snapshot) + media) {
                    zip.entry(if (file == snapshot) SNAPSHOT_NAME else file.name) {
                        file.inputStream().use { it.copyTo(this) }
                    }
                }
            }
        } finally {
            SQLiteDatabase.deleteDatabase(snapshot)
        }
    }

    suspend fun restore(source: File): BackupProfile {
        val staging = context.cacheDir.resolve("restore-${java.util.UUID.randomUUID()}")
        check(staging.mkdir())
        val dbName = "${staging.name}.db"
        val snapshot = context.getDatabasePath(dbName)
        val newMedia = mutableListOf<File>()
        var restored = false
        try {
            val profile = extract(source, snapshot, staging)
            val version = SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.version }
            val liveVersion = database.withTransaction { database.openHelper.writableDatabase.version }
            if (version > liveVersion) throw BackupException("Perbarui aplikasi sebelum memulihkan cadangan ini.")
            require(version > 0)
            PosDatabase.withBackupDatabase(context, dbName) { incoming ->
                val archived = incoming.openHelper.writableDatabase
                for (file in staging.listFiles().orEmpty()) {
                    val target = File(context.filesDir, file.name)
                    if (target.exists()) {
                        if (!target.sameContents(file)) throw BackupException("File gambar cadangan bentrok dengan penyimpanan perangkat.")
                    } else {
                        if (!file.renameTo(target)) throw IOException()
                        newMedia += target
                    }
                }
                withContext(NonCancellable) {
                    restoreDatabase(archived, profile)
                    restored = true
                }
            }
            return profile
        } finally {
            context.deleteDatabase(dbName)
            staging.deleteRecursively()
            if (!restored) newMedia.forEach { it.delete() }
        }
    }

    private suspend fun restoreDatabase(archived: SupportSQLiteDatabase, profile: BackupProfile) {
        database.withTransaction {
            val live = database.openHelper.writableDatabase
            val tables = live.businessTables()
            require(tables.toSet() == archived.businessTables().toSet())
            tables.asReversed().forEach { live.execSQL("DELETE FROM ${identifier(it)}") }
            for (table in tables) {
                if (table == "CartItem" || table == "StoreSettings") continue
                val name = identifier(table)
                val columns = live.query("SELECT * FROM $name LIMIT 0").use { it.columnNames }
                val selection = columns.joinToString(", ", transform = ::identifier)
                live.compileStatement(insertSql(table, columns)).use { statement ->
                    archived.query("SELECT $selection FROM $name").use { rows ->
                        while (rows.moveToNext()) {
                            columns.indices.forEach { statement.bindValue(it + 1, rows.getValue(it)) }
                            statement.executeInsert()
                        }
                    }
                }
            }
            if (archived.sequenceTable().isNotEmpty()) {
                archived.query("SELECT name, seq FROM sqlite_sequence").use { rows ->
                    while (rows.moveToNext()) {
                        val name = rows.getString(0)
                        if (name !in tables) continue
                        val seq = rows.getLong(1)
                        live.execSQL(
                            "UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = ?",
                            arrayOf(seq, name)
                        )
                        live.execSQL(
                            "INSERT INTO sqlite_sequence (name, seq) SELECT ?, ? " +
                                "WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name = ?)",
                            arrayOf(name, seq, name)
                        )
                    }
                }
            }
            database.dao().saveStoreSettings(profile.settings())
        }
    }

    private fun extract(source: File, snapshot: File, staging: File): BackupProfile {
        var profile: BackupProfile? = null
        val seen = mutableSetOf<String>()
        var remaining = MAX_EXTRACTED_BYTES
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && seen.add(entry.name))
                when (val name = entry.name) {
                    PROFILE_NAME -> profile = readBackupProfile(zip.readBounded(4096).toString(Charsets.UTF_8))
                    else -> {
                        val target = when {
                            name == SNAPSHOT_NAME -> snapshot
                            mediaName.matches(name) -> File(staging, name)
                            else -> throw IOException()
                        }
                        remaining -= target.outputStream().use { output -> zip.copyBounded(output, remaining) }
                    }
                }
                zip.closeEntry()
            }
        }
        val result = requireNotNull(profile)
        require(snapshot.isFile)
        for (name in listOf(result.store.logoName, result.qris)) {
            require(name.isEmpty() || name in seen)
        }
        return result
    }
}

private fun BackupProfile.toJson() = JSONObject().put("format", FORMAT)
    .put("name", store.name).put("address", store.address).put("logo", store.logoName).put("qris", qris)

private fun readBackupProfile(text: String): BackupProfile {
    val json = JSONObject(text)
    if (json.getInt("format") != FORMAT) throw BackupException("Format cadangan tidak didukung oleh versi aplikasi ini.")
    val name = json.getString("name")
    val address = json.getString("address")
    val logo = json.getString("logo")
    val qris = json.getString("qris")
    require(name.length in 1..60 && address.length <= 180)
    require(logo.isEmpty() || (logo.startsWith("logo-") && mediaName.matches(logo)))
    require(qris.isEmpty() || (qris.startsWith("qris-") && mediaName.matches(qris)))
    return BackupProfile(StoreProfile(name, address, logo), qris)
}

private fun SupportSQLiteDatabase.businessTables(): List<String> {
    val tables = query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name NOT IN ('android_metadata', 'room_master_table', 'room_table_modification_log') ORDER BY name"
    ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }
    val references = tables.associateWith { table ->
        query("PRAGMA foreign_key_list(${identifier(table)})").use { foreign ->
            buildSet { while (foreign.moveToNext()) add(foreign.getString(2)) } - table
        }
    }
    return buildList {
        while (size < tables.size) {
            val ready = tables.filter { it !in this && containsAll(references.getValue(it)) }
            require(ready.isNotEmpty())
            addAll(ready)
        }
    }
}

private fun SupportSQLiteDatabase.sequenceTable(): List<String> =
    query("SELECT name FROM sqlite_master WHERE name = 'sqlite_sequence'").use {
        if (it.moveToFirst()) listOf("sqlite_sequence") else emptyList()
    }

private fun identifier(name: String) = "\"${name.replace("\"", "\"\"")}\""

private fun insertSql(table: String, columns: Array<String>): String =
    "INSERT INTO ${identifier(table)} (${columns.joinToString(", ", transform = ::identifier)}) " +
        "VALUES (${columns.joinToString(", ") { "?" }})"

private fun android.database.Cursor.getValue(index: Int): Any? = when (getType(index)) {
    android.database.Cursor.FIELD_TYPE_NULL -> null
    android.database.Cursor.FIELD_TYPE_INTEGER -> getLong(index)
    android.database.Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
    android.database.Cursor.FIELD_TYPE_BLOB -> getBlob(index)
    else -> getString(index)
}

private fun androidx.sqlite.db.SupportSQLiteStatement.bindValue(index: Int, value: Any?) = when (value) {
    null -> bindNull(index)
    is Long -> bindLong(index, value)
    is Double -> bindDouble(index, value)
    is ByteArray -> bindBlob(index, value)
    else -> bindString(index, value as String)
}

private inline fun ZipOutputStream.entry(name: String, write: ZipOutputStream.() -> Unit) {
    putNextEntry(ZipEntry(name))
    write()
    closeEntry()
}

internal fun InputStream.copyBounded(output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var size = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) return size
        size += read
        if (size > limit) tooLarge()
        output.write(buffer, 0, read)
    }
}

internal fun InputStream.readBounded(limit: Long): ByteArray =
    java.io.ByteArrayOutputStream().also { copyBounded(it, limit) }.toByteArray()

private fun File.sameContents(other: File): Boolean {
    if (length() != other.length()) return false
    inputStream().buffered().use { first ->
        other.inputStream().buffered().use { second ->
            while (true) {
                val byte = first.read()
                if (byte != second.read()) return false
                if (byte == -1) return true
            }
        }
    }
}

private fun tooLarge(): Nothing = throw BackupException("Ukuran cadangan melebihi batas aplikasi.")

private class LimitedOutput(private val destination: OutputStream) : OutputStream() {
    private var size = 0L
    override fun write(value: Int) {
        reserve(1)
        destination.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        reserve(length)
        destination.write(bytes, offset, length)
    }

    private fun reserve(count: Int) {
        size += count
        if (size > MAX_BACKUP_BYTES) tooLarge()
    }

    override fun close() = destination.close()
}
