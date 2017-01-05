package de.qabel.box.storage.local

import de.qabel.box.storage.*
import de.qabel.box.storage.dto.BoxPath
import de.qabel.box.storage.local.database.LocalStorageDatabase
import de.qabel.box.storage.local.database.LocalStorageDatabaseFactory
import de.qabel.box.storage.local.repository.BoxLocalStorageRepository
import de.qabel.core.config.Identity
import de.qabel.core.crypto.CryptoUtils
import de.qabel.core.extensions.CoreTestCase
import de.qabel.core.extensions.createIdentity
import de.qabel.core.extensions.letApply
import de.qabel.core.extensions.randomFile
import de.qabel.core.repository.EntityManager
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class BoxLocalStorageTest : CoreTestCase {

    val identityA: Identity = createIdentity("Bob").apply { id = 1 }

    lateinit var deviceID: ByteArray
    lateinit var volumeA: BoxVolume
    lateinit var navigationA: BoxNavigation

    lateinit var remoteStorageDir: File
    lateinit var readBackend: LocalReadBackend
    lateinit var volumeTmpDir: File


    lateinit var testFile: File
    lateinit var testBoxFile: BoxFile

    lateinit var externalDir: File
    lateinit var storageDir: File
    lateinit var storage: LocalStorage

    @Before
    fun setUp() {
        remoteStorageDir = createTempDir("remote")
        volumeTmpDir = createTempDir("volume")
        storageDir = createTempDir("storage")
        externalDir = createTempDir("external")

        readBackend = LocalReadBackend(remoteStorageDir)
        val cryptoUtil = CryptoUtils()
        deviceID = cryptoUtil.getRandomBytes(16)
        volumeA = BoxVolumeImpl(readBackend, LocalWriteBackend(remoteStorageDir),
            identityA.primaryKeyPair, deviceID, volumeTmpDir, "prefix")
        volumeA.createIndex("qabel")
        navigationA = volumeA.navigate()


        testFile = File(externalDir, "testfile").letApply {
            val random = randomFile(100)
            FileUtils.copyFile(random, it)
            random.delete()
        }
        testBoxFile = navigationA.upload(testFile.name, testFile)
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val clientDatabase = LocalStorageDatabase(connection)
        clientDatabase.migrate()
        storage = BoxLocalStorage(storageDir, externalDir, cryptoUtil, BoxLocalStorageRepository(clientDatabase, EntityManager()))
    }

    @After
    fun cleanUp() {
        FileUtils.deleteDirectory(remoteStorageDir)
        FileUtils.deleteDirectory(volumeTmpDir)
        FileUtils.deleteDirectory(storageDir)
        FileUtils.deleteDirectory(externalDir)
    }

    @Test
    fun testFileRoundTrip() {
        val path = BoxPath.Root * testBoxFile.name
        val localDir = File(storageDir, "prefix")
        assertNull(storage.getBoxFile(path, testBoxFile))

        storage.storeFile(testFile, testBoxFile, path)
        val storedFile = storage.getBoxFile(path, testBoxFile) ?: throw AssertionError("Stored file not found!")
        assertEquals(1, localDir.list().size)
        assertFileEquals(storedFile, testFile)

        val testString = "THIS IS TEST"
        IOUtils.write(testString.toByteArray(), storedFile.outputStream())
        navigationA.overwrite(testBoxFile.name, storedFile)

        val boxFile = navigationA.listFiles().first()
        assertNull(storage.getBoxFile(path, boxFile))
        assertEquals(0, localDir.list().size)

        storage.storeFile(storedFile, boxFile, path)
        val storedFile2 = storage.getBoxFile(path, boxFile) ?: throw AssertionError("Stored file not found!")
        assertEquals(1, localDir.list().size)
        assertFileEquals(storedFile2, testFile)
        assertArrayEquals(testString.toByteArray(), FileUtils.readFileToByteArray(storedFile))
    }

    fun assertFileEquals(current: File, expected: File) {
        assertEquals(current.name, expected.name)
        assertArrayEquals(FileUtils.readFileToByteArray(current), FileUtils.readFileToByteArray(expected))
    }
}
