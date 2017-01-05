package de.qabel.box.storage.local.repository

import de.qabel.box.storage.dto.BoxPath
import de.qabel.box.storage.local.database.LocalStorageDatabase
import de.qabel.box.storage.local.repository.StorageEntryDB.PATH
import de.qabel.box.storage.local.repository.StorageEntryDB.PREFIX
import de.qabel.core.repository.EntityManager
import de.qabel.core.repository.framework.BaseRepository
import de.qabel.core.repository.sqlite.ClientDatabase

class BoxLocalStorageRepository(database: ClientDatabase,
                                entityManager: EntityManager) : LocalStorageRepository,
    BaseRepository<StorageEntry>(StorageEntryDB, StorageEntryResultAdapter(), database, entityManager) {

    override fun findEntry(prefix: String, path: BoxPath): StorageEntry {
        with(createEntityQuery()) {
            whereAndEquals(PREFIX, prefix)
            whereAndEquals(PATH, path.toString())
            return getSingleResult(queryBuilder = this)
        }
    }

    override fun findByPath(prefix: String, path: BoxPath): List<StorageEntry> {
        with(createEntityQuery()) {
            whereAndEquals(PREFIX, prefix)
          //  whereAndEquals(PATH, path.toString())
            return getResultList(queryBuilder = this)
        }
    }

}
