package de.qabel.box.storage.local

import de.qabel.box.storage.BoxFile
import de.qabel.box.storage.BoxFolder
import de.qabel.box.storage.BoxVolume
import de.qabel.box.storage.DirectoryMetadata
import de.qabel.box.storage.dto.BoxPath
import de.qabel.box.storage.exceptions.QblStorageException
import de.qabel.box.storage.exceptions.QblStorageNotFound
import de.qabel.box.storage.local.repository.EntryType
import de.qabel.box.storage.local.repository.LocalStorageRepository
import de.qabel.box.storage.local.repository.StorageEntry
import de.qabel.core.crypto.CryptoUtils
import de.qabel.core.extensions.letApply
import de.qabel.core.logging.QabelLog
import de.qabel.core.repository.exception.EntityNotFoundException
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.util.encoders.Hex
import java.io.File
import java.io.InputStream
import java.util.*

class BoxLocalStorage(private val storageFolder: File,
                      private val tmpFolder: File,
                      private val cryptoUtils: CryptoUtils,
                      private val repository: LocalStorageRepository) : LocalStorage, QabelLog {

    override fun getBoxFile(path: BoxPath.File,
                            boxFile: BoxFile): File? {
        return identifier(path, boxFile).let {
            debug("Get local file $it")
            getStorageEntry(it, { File(tmpFolder, boxFile.name) }, { it })
        }
    }

    override fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File): File {
        identifier(path, boxFile).let {
            debug("Store local file $it}")
            updateStorageEntry(it, input)
        }
        return getBoxFile(path, boxFile) ?: throw QblStorageException("Store local file failed!")
    }

    override fun getDirectoryMetadata(boxVolume: BoxVolume, path: BoxPath.Folder, boxFolder: BoxFolder): DirectoryMetadata? {
        return identifier(path, boxFolder, boxVolume.config.prefix).let {
            debug("Get local dm $it")
            getStorageEntry(it,
                { File.createTempFile("dir", "db2", tmpFolder) },
                { file -> boxVolume.config.directoryFactory.open(file, it.currentRef) })
        }
    }

    override fun storeDirectoryMetadata(path: BoxPath.Folder, boxFolder: BoxFolder, directoryMetadata: DirectoryMetadata, prefix: String) {
        identifier(path, boxFolder, prefix, Hex.toHexString(directoryMetadata.version)).let {
            debug("Store local dm $it")
            updateStorageEntry(it, directoryMetadata.path.inputStream())
        }
    }

    private fun <T> getStorageEntry(identifier: StorageIdentifier, targetFile: () -> File, readFile: (File) -> T?): T? {
        try {
            val entry = repository.findEntry(identifier.prefix, identifier.path, identifier.type)
            val file = getLocalFile(entry)
            if (entry.ref == identifier.currentRef &&
                (identifier.modifiedTag.isBlank() || identifier.modifiedTag == entry.modifiedTag)) {
                if (file.exists()) {
                    val tmp = targetFile()
                    if (cryptoUtils.decryptFileAuthenticatedSymmetricAndValidateTag(file.inputStream(), tmp, identifier.key)) {
                        return readFile(tmp)
                    } else {
                        throw QblStorageNotFound("Invalid key")
                    }
                }
                repository.delete(entry.id)
            }
            file.delete()
            repository.delete(entry.id)
        } catch (ex: EntityNotFoundException) {
        }
        return null
    }

    private fun updateStorageEntry(identifier: StorageIdentifier, input: InputStream) {
        val (entry: StorageEntry, refreshFile: Boolean) = try {
            val localEntry = repository.findEntry(identifier.prefix, identifier.path, identifier.type)
            if (localEntry.ref != identifier.currentRef || localEntry.modifiedTag != identifier.modifiedTag) {
                Pair(localEntry.letApply {
                    getLocalFile(it).apply { if (exists()) delete() }
                    it.ref = identifier.currentRef
                    it.modifiedTag = identifier.modifiedTag
                    debug("Override entry $identifier")
                }, true)
            } else {
                debug("entry is up to date $identifier")
                Pair(localEntry, false)
            }
        } catch (ex: EntityNotFoundException) {
            val currentTime = Date()
            Pair(StorageEntry(identifier.prefix, identifier.path, identifier.currentRef,
                identifier.modifiedTag, identifier.type,
                currentTime, currentTime).letApply {
                repository.persist(it)
                debug("Stored new entry $identifier")
            }, true)
        }

        if (refreshFile) {
            val storageFile = getLocalFile(identifier, true)
            if (!cryptoUtils.encryptStreamAuthenticatedSymmetric(input,
                storageFile.outputStream(), identifier.key, null)) {
                throw QblStorageException("Encryption failed")
            }
        }

        val currentTime = Date()
        entry.accessTime = currentTime
        entry.storageTime = currentTime
        repository.update(entry)
    }

    private fun getLocalFile(storageIdentifier: StorageIdentifier, createIfRequired: Boolean = false): File {
        val folder = File(storageFolder, storageIdentifier.prefix)
        if (!folder.exists() && createIfRequired) {
            if (!folder.mkdirs()) {
                throw QblStorageException("Cannot create storage folders!")
            }
        }
        val file = File(folder, storageIdentifier.currentRef)
        if (!file.exists() && createIfRequired) {
            if (!file.createNewFile()) {
                throw QblStorageException("Cannot create new storage file!")
            }
        }
        return file
    }

    private fun getLocalFile(storageEntry: StorageEntry): File {
        val folder = File(storageFolder, storageEntry.prefix)
        val file = File(folder, storageEntry.ref)
        return file
    }

    private data class StorageIdentifier(val path: BoxPath, val prefix: String, val type: EntryType,
                                         val currentRef: String, val key: KeyParameter,
                                         val modifiedTag: String) {
        override fun toString(): String = "${type.name}\t$path\t$currentRef"
    }

    private fun identifier(path: BoxPath.Folder, boxFolder: BoxFolder, prefix: String, modifiedTag: String = "") =
        StorageIdentifier(path, prefix, EntryType.DIRECTORY_METADATA, boxFolder.ref,
            KeyParameter(boxFolder.key), modifiedTag)

    private fun identifier(path: BoxPath.File, boxFile: BoxFile) =
        StorageIdentifier(path, boxFile.prefix, EntryType.FILE, boxFile.block,
            KeyParameter(boxFile.key), boxFile.mtime.toString())

}
