package de.qabel.box.storage.local

import de.qabel.box.storage.BoxFile
import de.qabel.box.storage.BoxFolder
import de.qabel.box.storage.BoxVolume
import de.qabel.box.storage.DirectoryMetadata
import de.qabel.box.storage.dto.BoxPath
import java.io.File
import java.io.InputStream

interface LocalStorage {

    fun getBoxFile(path: BoxPath.File, boxFile: BoxFile): File?

    fun storeFile(plainFile: File, boxFile: BoxFile, path: BoxPath.File)
    fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File)

    fun storeDirectoryMetadata(path: BoxPath.Folder, boxFolder: BoxFolder, directoryMetadata: DirectoryMetadata, prefix: String)
    fun getDirectoryMetadata(boxVolume: BoxVolume, path: BoxPath.Folder, boxFolder: BoxFolder): DirectoryMetadata?
}
