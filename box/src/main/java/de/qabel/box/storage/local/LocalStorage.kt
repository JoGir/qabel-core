package de.qabel.box.storage.local

import de.qabel.box.storage.*
import de.qabel.box.storage.dto.BoxPath
import java.io.File
import java.io.InputStream

interface LocalStorage {

    fun getBoxFile(path: BoxPath.File, boxFile: BoxFile): File?
    fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File) : File

    fun storeDirectoryMetadata(path: BoxPath.FolderLike, boxFolder: BoxFolder, directoryMetadata: DirectoryMetadata, prefix: String)
    fun getDirectoryMetadata(boxVolume: BoxVolume, path: BoxPath.FolderLike, boxFolder: BoxFolder): DirectoryMetadata?
    open fun storeDmByNavigation(navigation: BoxNavigation)
}
