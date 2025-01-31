package io.github.goodgoodjm.otter.core

import io.github.goodgoodjm.otter.core.resourceresolver.ResourceResolver
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.CurrentDateTime
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.Reader
import javax.script.ScriptEngineManager

class Otter(
    private val config: OtterConfig,
) {
    companion object : Logger {
        fun from(config: OtterConfig) = Otter(config)
    }

    private fun migrationScope(block: Transaction.() -> Unit) {
        logger.info("Start migration.")
        val database = Database.connect(
            config.url,
            config.driverClassName,
            config.user,
            config.password
        )

        transaction {
            block()
        }

        logger.info("Success migration.")
        TransactionManager.closeAndUnregister(database)
    }

    fun up() = migrationScope {
        MigrationProcess(this, config.migrationPath, config.showSql).exec()
    }
}

object MigrationTable : IntIdTable("otter_migration") {
    val filename = varchar("filename", 255).uniqueIndex()
    val comment = varchar("comment", 255)
    private val createdAt = datetime("created_at").defaultExpression(CurrentDateTime())

    fun last() = selectAll().orderBy(createdAt to SortOrder.DESC).firstOrNull()
}

class MigrationProcess(
    private val transaction: Transaction,
    private val migrationPath: String,
    private val showSql: Boolean,
) {
    companion object : Logger

    fun exec() {
        createMigrationTable()
        migration()
    }

    private fun createMigrationTable() {
        if (MigrationTable.exists()) {
            return
        }
        SchemaUtils.create(MigrationTable)
    }

    private fun migration() {
        val latestAppliedMigration = MigrationTable.last()
        val latestFilename = if (latestAppliedMigration == null) {
            "".also {
                logger.debug("There is no applied migrations. All migrations would be applied.")
            }
        } else {
            latestAppliedMigration[MigrationTable.filename].also {
                logger.debug("There are applied migrations(last=$it).")
            }
        }

        val migrations = loadMigrations()
        for ((name, migration) in migrations) {
            if (latestFilename >= name) {
                logger.debug("$name is already migrated, will be skipped.")
                continue
            }

            migration.up()
            migration.contexts.flatMap { it.resolve() }.forEach {
                if (showSql) logger.info(it)
                transaction.exec(it)
            }

            MigrationTable.insert {
                it[filename] = name
                it[comment] = migration.comment
            }

            transaction.commit()
        }
    }

    private fun loadMigrations(): Map<String, Migration> = ResourceResolver().resolveEntries(migrationPath)
        .sortedBy { it }
        .also { logger.debug("Target files : $it") }
        .associateWith { this::class.java.classLoader.getResource(it) }
        .filterValues { it != null }
        .mapKeys { it.key.split("/").last() }
        .mapValues { evalMigration(it.value!!.openStream().reader()) }

    private fun evalMigration(reader: Reader): Migration {
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        return engine.eval(reader) as Migration
    }
}

