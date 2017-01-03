package de.qabel.box.storage.local.repository

import de.qabel.box.storage.dto.BoxPath
import de.qabel.core.repository.framework.BaseEntity
import de.qabel.core.repository.framework.PersistableEnum
import java.util.*


data class StorageEntry(val prefix: String, val path: BoxPath,
                        var block: String, var modifiedTag: String,
                        val type: EntryType,
                        var storageTime : Date = Date(),
                        override var id: Int = 0) : BaseEntity

enum class EntryType(override val type: Int) : PersistableEnum<Int> {
    FILE(1)
}
