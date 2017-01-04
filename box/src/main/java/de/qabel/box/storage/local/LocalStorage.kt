package de.qabel.box.storage.local

import de.qabel.box.storage.BoxFile
import de.qabel.box.storage.dto.BoxPath
import java.io.File
import java.io.InputStream

interface LocalStorage {

    fun getBoxFile(path: BoxPath.File, boxFile: BoxFile): File?

    fun storeFile(plainFile: File, boxFile: BoxFile, path: BoxPath.File)

    fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File)
}
