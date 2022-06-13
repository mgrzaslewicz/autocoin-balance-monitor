package autocoin.balance.health.db

import autocoin.balance.health.HealthCheck
import autocoin.balance.health.HealthCheckResult
import mu.KLogging
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

class DbHealthCheck(private val datasource: AtomicReference<DataSource>) : HealthCheck {
    private companion object : KLogging()

    private fun getDbConnectionStatus(): Pair<Boolean, String?> {
        return try {
            if (datasource.get() != null) {
                datasource.get().connection.prepareStatement("select 1").execute()
                Pair(true, null)
            } else {
                Pair(false, "Datasource not created yet")
            }
        } catch (e: Exception) {
            logger.error(e) { "DB connection issue" }
            Pair(false, e.message)
        }
    }

    override fun doHealthCheck(): HealthCheckResult {

        val dbConnectionStatus = getDbConnectionStatus()
        return HealthCheckResult(
            description = "PostgreSQL connection",
            healthy = dbConnectionStatus.first,
            unhealthyReasons = listOfNotNull(
                if (!dbConnectionStatus.first) "DB connection issue, reason: ${dbConnectionStatus.second}" else null
            ),
            healthCheckClass = this.javaClass
        )
    }
}
