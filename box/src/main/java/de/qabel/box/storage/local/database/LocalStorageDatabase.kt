package de.qabel.box.storage.local.database

import de.qabel.box.storage.DatabaseMigrationProvider
import de.qabel.box.storage.local.database.migrations.LocalStorageDatabaseMigrations
import de.qabel.core.repository.sqlite.AbstractClientDatabase
import de.qabel.core.repository.sqlite.PragmaVersionAdapter
import de.qabel.core.repository.sqlite.VersionAdapter
import java.sql.Connection

class LocalStorageDatabase(
    connection: Connection,
    versionAdapter: VersionAdapter = PragmaVersionAdapter(connection)
) : AbstractClientDatabase(connection),
    DatabaseMigrationProvider by LocalStorageDatabaseMigrations() {

    override var version by versionAdapter

}
