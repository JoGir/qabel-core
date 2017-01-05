package de.qabel.box.storage.local

import de.qabel.box.storage.BoxFile
import de.qabel.box.storage.dto.BoxPath
import de.qabel.box.storage.exceptions.QblStorageException
import de.qabel.box.storage.local.repository.EntryType
import de.qabel.box.storage.local.repository.LocalStorageRepository
import de.qabel.box.storage.local.repository.StorageEntry
import de.qabel.core.crypto.CryptoUtils
import de.qabel.core.extensions.letApply
import de.qabel.core.repository.exception.EntityNotFoundException
import org.spongycastle.crypto.params.KeyParameter
import java.io.File
import java.io.InputStream
import java.util.*

class BoxLocalStorage(private val storageFolder: File,
                      private val tmpFolder: File,
                      private val cryptoUtils: CryptoUtils,
                      private val repository: LocalStorageRepository) : LocalStorage {

    override fun getBoxFile(path: BoxPath.File,
                            boxFile: BoxFile): File? {
        try {
            val entry = repository.findEntry(boxFile.prefix, path)
            val file = getLocalFile(boxFile)
            if (checkExisting(boxFile, entry)) {
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

    override fun storeFile(plainFile: File, boxFile: BoxFile, path: BoxPath.File) : Unit =
        storeFile(plainFile.inputStream(), boxFile, path)

    override fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File) {
        val entry: StorageEntry = try {
            repository.findEntry(boxFile.prefix, path).letApply {
                if (!checkExisting(boxFile, it)) {
                    it.block = boxFile.block
                    it.modifiedTag = boxFile.mtime.toString()
                }
            }
        } catch (ex: EntityNotFoundException) {
            StorageEntry(boxFile.prefix, path, boxFile.block, boxFile.mtime.toString(), EntryType.FILE, Date(), Date()).letApply {
                repository.persist(it)
            }
        }
        val storageFile = getLocalFile(boxFile, true)
        if (!cryptoUtils.encryptStreamAuthenticatedSymmetric(input, storageFile.outputStream(), KeyParameter(boxFile.key), null)) {
            throw QblStorageException("Encryption failed")
        }
        entry.storageTime = Date()
        repository.update(entry)
    }

    private fun checkExisting(boxFile: BoxFile, storageEntry: StorageEntry): Boolean {
        val storageFile = getLocalFile(storageEntry)
        if (boxFile.mtime.toString() != storageEntry.modifiedTag ||
            boxFile.block != storageEntry.block) {
            if (storageFile.exists() && !storageFile.delete()) {
                throw QblStorageException("Cannot delete outdated file!")
            }
            return false
        }
        return storageFile.exists()
    }

    private fun getLocalFile(boxFile: BoxFile, createIfRequired: Boolean = false): File {
        val folder = File(storageFolder, boxFile.prefix)
        if (!folder.exists() && createIfRequired) {
            if (!folder.mkdirs()) {
                throw QblStorageException("Cannot create storage folders!")
            }
        }
        val file = File(folder, boxFile.block)
        if (!file.exists() && createIfRequired) {
            if (!file.createNewFile()) {
                throw QblStorageException("Cannot create new storage file!")
            }
        }
        return file
    }

    private fun getLocalFile(storageEntry: StorageEntry): File {
        val folder = File(storageFolder, storageEntry.prefix)
        val file = File(folder, storageEntry.block)
        return file
    }
}
