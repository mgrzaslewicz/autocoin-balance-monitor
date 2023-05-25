package autocoin

import autocoin.balance.app.createDatasource
import autocoin.balance.app.createJdbi
import autocoin.balance.app.createLiquibase
import liquibase.Contexts
import org.jdbi.v3.core.Jdbi
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

class TestDb {
    data class StartedDatabase(
        val datasource: DataSource,
        val container: PostgreSQLContainer<*>,
        val jdbi: Jdbi,
    ) {
        fun runMigrations() {
            val liquibase = createLiquibase(datasource)
            liquibase.update(Contexts())
        }

    }

    companion object {
        fun startDatabase(): StartedDatabase {
            val dbPassword = "samplePassword"
            val dbUser = "sampleUser"
            val dbContainer = PostgreSQLContainer("postgres:11.0")
            dbContainer.withUsername(dbUser)
            dbContainer.withPassword(dbPassword)
            // use in memory storage for faster execution
            dbContainer.withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
            dbContainer.start()

            val datasource = createDatasource(dbContainer.jdbcUrl, "sampleUser", "samplePassword")
            return StartedDatabase(
                datasource = datasource,
                container = dbContainer,
                jdbi = createJdbi(datasource),
            ).apply { runMigrations() }
        }
    }
}
