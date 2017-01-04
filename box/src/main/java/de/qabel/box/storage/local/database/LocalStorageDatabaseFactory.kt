package de.qabel.box.storage.local.database

import de.qabel.box.storage.AbstractMetadata
import de.qabel.box.storage.DirectoryMetadataFactory
import de.qabel.box.storage.exceptions.QblStorageCorruptMetadata
import de.qabel.box.storage.exceptions.QblStorageException
import de.qabel.box.storage.exceptions.QblStorageIOFailure
import de.qabel.box.storage.jdbc.DirectoryMetadataDatabase
import de.qabel.box.storage.jdbc.JdbcDirectoryMetadata
import de.qabel.core.repository.sqlite.tryWith
import java.io.File
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

class LocalStorageDatabaseFactory @JvmOverloads constructor(
    storageDirectory: File,
    val deviceId: ByteArray,
    val jdbcPrefix: String = AbstractMetadata.DEFAULT_JDBC_PREFIX
) {

    private val storageDBFile = File(storageDirectory, ".storage_db")

    /**
     * Open local storage database
     */
    @Throws(QblStorageException::class)
    fun openDatabase(): LocalStorageDatabase {
        try {
            if(!storageDBFile.exists()) storageDBFile.mkdirs() && storageDBFile.createNewFile()

            val connection = DriverManager.getConnection(jdbcPrefix + storageDBFile.absolutePath)
            connection.autoCommit = true
            val db = LocalStorageDatabase(connection)
            db.migrate()
            return db
        } catch (e: SQLException) {
            throw QblStorageCorruptMetadata(e)
        }
    }

}
