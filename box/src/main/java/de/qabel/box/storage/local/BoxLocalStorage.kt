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
import de.qabel.core.config.Prefix
import de.qabel.core.crypto.CryptoUtils
import de.qabel.core.extensions.letApply
import de.qabel.core.logging.QabelLog
import de.qabel.core.repository.exception.EntityNotFoundException
import org.spongycastle.crypto.params.KeyParameter
import java.io.File
import java.io.InputStream
import java.util.*

class BoxLocalStorage(private val storageFolder: File,
                      private val tmpFolder: File,
                      private val cryptoUtils: CryptoUtils,
                      private val repository: LocalStorageRepository) : LocalStorage, QabelLog {

    override fun getBoxFile(path: BoxPath.File,
                            boxFile: BoxFile): File? {
        try {
            debug("Get local file ${path.name}")
            val entry = repository.findEntry(boxFile.prefix, path, EntryType.FILE)
            val file = getLocalFile(boxFile.prefix, boxFile.block)
            if (checkExisting(boxFile, entry)) {
                debug("Found local file ${path.name}")
                val externalFile = File(tmpFolder, boxFile.name)
                if (!cryptoUtils.decryptFileAuthenticatedSymmetricAndValidateTag(file.inputStream(),
                    externalFile, KeyParameter(boxFile.key))) {
                    throw QblStorageException("Decryption failed")
                }
                entry.accessTime = Date()
                repository.update(entry)
                return externalFile
            } else {
                repository.delete(entry.id)
            }
        } catch (ex: EntityNotFoundException) {
        }
        return null
    }

    override fun storeFile(plainFile: File, boxFile: BoxFile, path: BoxPath.File): Unit =
        storeFile(plainFile.inputStream(), boxFile, path)

    override fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File) {
        debug("Store local file ${path.name}")
        val entry: StorageEntry = try {
            repository.findEntry(boxFile.prefix, path, EntryType.FILE).letApply {
                if (!checkExisting(boxFile, it)) {
                    it.ref = boxFile.block
                    it.modifiedTag = boxFile.mtime.toString()
                }
                debug("Override local file ${path.name}")
            }
        } catch (ex: EntityNotFoundException) {
            StorageEntry(boxFile.prefix, path, boxFile.block, boxFile.mtime.toString(), EntryType.FILE, Date(), Date()).letApply {
                repository.persist(it)
                debug("Stored new local file ${path.name}")
            }
        }
        val storageFile = getLocalFile(boxFile.prefix, boxFile.block, true)
        if (!cryptoUtils.encryptStreamAuthenticatedSymmetric(input, storageFile.outputStream(), KeyParameter(boxFile.key), null)) {
            throw QblStorageException("Encryption failed")
        }
        entry.storageTime = Date()
        repository.update(entry)
    }

    private fun checkExisting(boxFile: BoxFile, storageEntry: StorageEntry): Boolean {
        val storageFile = getLocalFile(storageEntry)
        if (boxFile.mtime.toString() != storageEntry.modifiedTag ||
            boxFile.block != storageEntry.ref) {
            if (storageFile.exists() && !storageFile.delete()) {
                throw QblStorageException("Cannot delete outdated file!")
            }
            return false
        }
        return storageFile.exists()
    }

    private fun getLocalFile(prefix: String, ref: String, createIfRequired: Boolean = false): File {
        val folder = File(storageFolder, prefix)
        if (!folder.exists() && createIfRequired) {
            if (!folder.mkdirs()) {
                throw QblStorageException("Cannot create storage folders!")
            }
        }
        val file = File(folder, ref)
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

    override fun getDirectoryMetadata(boxVolume: BoxVolume, path: BoxPath.Folder, boxFolder: BoxFolder): DirectoryMetadata? {
        try {
            debug("Get local dm ${path.name}")
            val entry = repository.findEntry(boxVolume.config.prefix, path, EntryType.DIRECTORY_METADATA)
            val file = getLocalFile(boxVolume.config.prefix, boxFolder.ref)
            if (file.exists()) {
                if (entry.ref == boxFolder.ref) {
                    val tmp = File.createTempFile("dir", "db2", tmpFolder)
                    val key = KeyParameter(boxFolder.key)
                    if (cryptoUtils.decryptFileAuthenticatedSymmetricAndValidateTag(file.inputStream(), tmp, key)) {
                        return boxVolume.config.directoryFactory.open(tmp, boxFolder.ref)
                    } else {
                        throw QblStorageNotFound("Invalid key")
                    }
                } else {
                    file.delete()
                }
            }
            repository.delete(entry.id)
        } catch (ex: EntityNotFoundException) {
        }
        return null
    }

    override fun storeDirectoryMetadata(path: BoxPath.Folder, boxFolder: BoxFolder, directoryMetadata: DirectoryMetadata, prefix: String) {
        debug("Store local dm ${path.name}")
        val entry: StorageEntry = try {
            repository.findEntry(prefix, path, EntryType.DIRECTORY_METADATA).letApply {
                if (it.ref != boxFolder.ref) {
                    getLocalFile(it).apply { if (exists()) delete() }
                    it.ref = boxFolder.ref
                }
                debug("Override local dm ${path.name}")
            }
        } catch (ex: EntityNotFoundException) {
            val currentTime = Date()
            StorageEntry(prefix, path, boxFolder.ref, currentTime.toString(), EntryType.DIRECTORY_METADATA,
                currentTime, currentTime).letApply {
                repository.persist(it)
                debug("Stored new local dm ${path.name}")
            }
        }
        val storageFile = getLocalFile(prefix, boxFolder.ref, true)
        if (!cryptoUtils.encryptStreamAuthenticatedSymmetric(directoryMetadata.path.inputStream(),
            storageFile.outputStream(), KeyParameter(boxFolder.key), null)) {

            throw QblStorageException("Encryption failed")
        }
        entry.storageTime = Date()
        repository.update(entry)
    }

}
