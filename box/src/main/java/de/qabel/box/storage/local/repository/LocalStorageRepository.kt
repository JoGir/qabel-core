package de.qabel.box.storage.local.repository

import de.qabel.box.storage.dto.BoxPath
import de.qabel.core.repository.framework.Repository

interface LocalStorageRepository : Repository<StorageEntry> {

    fun findEntry(prefix: String, path: BoxPath) : StorageEntry
    fun findByPath(prefix : String, path : BoxPath) : List<StorageEntry>

}
