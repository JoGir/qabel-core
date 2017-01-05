package de.qabel.box.storage.local.database.migrations

import de.qabel.box.storage.DatabaseMigrationProvider
import de.qabel.box.storage.local.database.migrations.LSMigration1460997045Init
import de.qabel.core.repository.sqlite.migration.AbstractMigration
import java.sql.Connection


class LocalStorageDatabaseMigrations : DatabaseMigrationProvider {

    override fun getMigrations(connection: Connection): Array<out AbstractMigration> =
        arrayOf(LSMigration1460997045Init(connection))

}
