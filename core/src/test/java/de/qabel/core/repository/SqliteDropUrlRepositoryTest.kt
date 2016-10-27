package de.qabel.core.repository

import de.qabel.core.config.Contact
import de.qabel.core.config.factory.DropUrlGenerator
import de.qabel.core.config.factory.IdentityBuilder
import de.qabel.core.crypto.QblECPublicKey
import de.qabel.core.drop.DropURL
import de.qabel.core.repository.sqlite.*
import de.qabel.core.repository.sqlite.hydrator.DropURLHydrator
import org.hamcrest.Matchers.*
import java.util.*
import org.junit.Assert.assertThat
import org.junit.Test


class SqliteDropUrlRepositoryTest : AbstractSqliteRepositoryTest<SqliteDropUrlRepository>() {

    lateinit var contactRepo: ContactRepository

    val dropGenerator = DropUrlGenerator("http://localhost")
    val identityA = IdentityBuilder(dropGenerator).withAlias("identityA").build()
    val contactA = Contact("contactA", LinkedList<DropURL>(), QblECPublicKey("test13".toByteArray()));
    val contactB = Contact("contactB", LinkedList<DropURL>(), QblECPublicKey("test24".toByteArray()));

    override fun createRepo(clientDatabase: ClientDatabase, em: EntityManager): SqliteDropUrlRepository? {
        val identityRepo = SqliteIdentityRepository(clientDatabase, em)
        contactRepo = SqliteContactRepository(clientDatabase, em, SqliteDropUrlRepository(clientDatabase), identityRepo)
        identityRepo.save(identityA)
        contactRepo.save(contactA, identityA)
        contactRepo.save(contactB, identityA)
        return SqliteDropUrlRepository(clientDatabase, DropURLHydrator())
    }

    @Test
    fun testSave() {
        val dropUrl = dropGenerator.generateUrl()
        contactA.addDrop(dropUrl)
        repo.store(contactA)

        assertThat(repo.findAll(contactA), contains(dropUrl))
    }

    @Test
    fun testDelete() {
        val dropUrl = dropGenerator.generateUrl()
        contactA.addDrop(dropUrl)
        repo.store(contactA)
        assertThat(repo.findAll(contactA), contains(dropUrl))

        repo.delete(contactA)

        assertThat(repo.findAll(contactA), hasSize(0))
    }

    @Test
    fun testFindAll() {
        val dropUrls = listOf(dropGenerator.generateUrl(), dropGenerator.generateUrl(), dropGenerator.generateUrl())
        dropUrls.forEach { contactA.addDrop(it) }
        repo.store(contactA)

        assertThat(repo.findAll(contactA), containsInAnyOrder(*dropUrls.toTypedArray()))
    }

    @Test
    fun testFindByContactIds() {
        val dropUrlsA = listOf(dropGenerator.generateUrl(), dropGenerator.generateUrl())
        dropUrlsA.forEach { contactA.addDrop(it) }
        repo.store(contactA)

        val dropUrlB = dropGenerator.generateUrl()
        contactB.addDrop(dropUrlB)
        repo.store(contactB)

        val result = repo.findDropUrls(listOf(contactA.id, contactB.id))
        assertThat(result.keys, hasSize(2))
        assertThat(result.keys, containsInAnyOrder(contactA.id, contactB.id))

        val resultA = result[contactA.id]
        assertThat(resultA, hasSize(2))
        assertThat(resultA, containsInAnyOrder(*dropUrlsA.toTypedArray()))

        val resultB = result[contactB.id]
        assertThat(resultB, hasSize(1))
        assertThat(resultB, contains(dropUrlB))
    }


}
