package autocoin

import autocoin.balance.app.createDatasource
import autocoin.balance.app.createLiquibase
import liquibase.Contexts
import org.testcontainers.containers.PostgreSQLContainer
import java.util.Map
import javax.sql.DataSource

class TestDb {
    data class StartedDatabase(
        val datasource: DataSource,
        val container: PostgreSQLContainer<*>,
    )

    companion object {
        fun startDatabase(): StartedDatabase {
            val dbPassword = "samplePassword"
            val dbUser = "sampleUser"
            val dbContainer = PostgreSQLContainer("postgres:11.0")
            dbContainer.withUsername(dbUser)
            dbContainer.withPassword(dbPassword)
            // use in memory storage for faster execution
            dbContainer.withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
            dbContainer.start()

            val datasource = createDatasource(dbContainer.jdbcUrl, "sampleUser", "samplePassword")
            val liquibase = createLiquibase(datasource)
            liquibase.update(Contexts())
            return StartedDatabase(
                datasource = datasource,
                container = dbContainer,
            )
        }
    }
}
