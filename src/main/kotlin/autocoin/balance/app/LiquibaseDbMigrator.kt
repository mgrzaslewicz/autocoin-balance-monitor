package autocoin.balance.app

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import javax.sql.DataSource

class LiquibaseDbMigrator(private val datasource: DataSource) {
    fun migrate() {
        val liquibase = Liquibase(
            /* changeLogFile = */ "dbschema.sql",
            /* resourceAccessor = */ ClassLoaderResourceAccessor(),
            /* conn = */ JdbcConnection(datasource.connection)
        )
        liquibase.update(Contexts())
        liquibase.close()
    }
}
