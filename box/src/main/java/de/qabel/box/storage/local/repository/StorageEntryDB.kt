package de.qabel.box.storage.local.repository

import de.qabel.box.storage.dto.BoxPath
import de.qabel.box.storage.local.repository.StorageEntryDB.ACCESS_TIME
import de.qabel.box.storage.local.repository.StorageEntryDB.BLOCK
import de.qabel.box.storage.local.repository.StorageEntryDB.MODIFIED_TAG
import de.qabel.box.storage.local.repository.StorageEntryDB.PATH
import de.qabel.box.storage.local.repository.StorageEntryDB.PREFIX
import de.qabel.box.storage.local.repository.StorageEntryDB.STORAGE_TIME
import de.qabel.box.storage.local.repository.StorageEntryDB.TYPE
import de.qabel.core.repository.EntityManager
import de.qabel.core.repository.framework.DBField
import de.qabel.core.repository.framework.DBRelation
import de.qabel.core.repository.sqlite.hydrator.BaseEntityResultAdapter
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet

object StorageEntryDB : DBRelation<StorageEntry> {

    override val TABLE_NAME = "storage_entries"
    override val TABLE_ALIAS = "se"

    override val ID = field("id")
    val STORAGE_TIME = field("storage_time")
    val ACCESS_TIME = field("access_time")

    val PREFIX = field("prefix")
    val PATH = field("path")
    val BLOCK = field("block")
    val TYPE = field("type")
    val MODIFIED_TAG = field("modified_tag")

    override val ENTITY_FIELDS: List<DBField> = listOf(PREFIX, PATH, BLOCK, MODIFIED_TAG, STORAGE_TIME, ACCESS_TIME, TYPE)

    override val ENTITY_CLASS: Class<StorageEntry> = StorageEntry::class.java

    override fun applyValues(startIndex: Int, statement: PreparedStatement, model: StorageEntry): Int =
        with(statement) {
            var i = startIndex
            statement.setString(i++, model.prefix)
            statement.setString(i++, model.path.toString())
            statement.setString(i++, model.block)
            statement.setString(i++, model.modifiedTag)
            statement.setLong(i++, model.storageTime.time)
            statement.setLong(i++, model.accessTime.time)
            statement.setInt(i++, model.type.type)
            return i
        }


}

class StorageEntryResultAdapter : BaseEntityResultAdapter<StorageEntry>(StorageEntryDB) {

    override fun hydrateEntity(entityId: Int, resultSet: ResultSet, entityManager: EntityManager, detached: Boolean): StorageEntry {
        with(resultSet) {
            val type = enumValue(getInt(TYPE.alias()), EntryType.values())
            return StorageEntry(getString(PREFIX.alias()),
                createBoxPath(getString(PATH.alias()), type),
                getString(BLOCK.alias()),
                getString(MODIFIED_TAG.alias()),
                type, getDate(STORAGE_TIME.alias()),
                getDate(ACCESS_TIME.alias()),
                entityId)
        }
    }

    private fun createBoxPath(pathString: String, type: EntryType): BoxPath {
        var path: BoxPath.FolderLike = BoxPath.Root
        val parts = pathString.split("/")
        parts.forEachIndexed { i, part ->
            if (i < parts.size - 1) {
                path = BoxPath.Folder(part, path)
            }
        }
        return when (type) {
            EntryType.FILE -> BoxPath.File(parts.last(), path)
        }
    }

}