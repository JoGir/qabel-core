package de.qabel.box.storage.local.database.migrations

import de.qabel.core.repository.sqlite.migration.AbstractMigration
import java.sql.Connection

class LSMigration1460997045Init(connection: Connection) : AbstractMigration(connection) {

    override fun getVersion() = 1460997045L

    override fun up() {
        execute("""CREATE TABLE IF NOT EXISTS storage_entries (
                id INTEGER PRIMARY KEY,
                prefix VARCHAR(255)NOT NULL,
                path TEXT NOT NULL,
                block LONG NOT NULL,
                type INTEGER NOT NULL,
                modified_tag VARCHAR(255) NOT NULL,
                storage_time DATE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                access_time DATE NOT NULL DEFAULT CURRENT_TIMESTAMP)""")
    }

    override fun down() {
        execute("DROP TABLE storage_entries")
    }
}
