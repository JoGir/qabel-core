package de.qabel.box.storage.local

import de.qabel.box.storage.BoxFile
import de.qabel.box.storage.dto.BoxPath
import org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.*

class MockLocalStorage : LocalStorage {

    private val files: MutableMap<String, Pair<String, ByteArray>> = HashMap()

    private fun key(path: BoxPath, file: BoxFile) = path.toString() + file.prefix

    override fun getBoxFile(path: BoxPath.File, boxFile: BoxFile): File? {
        val key = key(path, boxFile)
        if (files.containsKey(key)) {
            val entry = files[key]!!
            if (entry.first == boxFile.block) {
                val file = File.createTempFile(boxFile.name, "")
                IOUtils.copy(ByteArrayInputStream(entry.second), file.outputStream())
                return file
            } else {
                files.remove(key)
            }
        }
        return null
    }

    override fun storeFile(plainFile: File, boxFile: BoxFile, path: BoxPath.File) = storeFile(plainFile.inputStream(), boxFile, path)

    override fun storeFile(input: InputStream, boxFile: BoxFile, path: BoxPath.File) {
        files.put(key(path, boxFile), Pair(boxFile.block, IOUtils.toByteArray(input)))
    }

}
