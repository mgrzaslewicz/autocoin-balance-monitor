package autocoin.balance.health.db

import autocoin.balance.health.HealthCheck
import autocoin.balance.health.HealthCheckResult
import com.zaxxer.hikari.HikariDataSource
import mu.KLogging
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicReference

class DbHealthCheck(private val datasource: AtomicReference<HikariDataSource>) : HealthCheck {
    private companion object : KLogging()

    private fun getDbConnectionStatus(): Pair<Boolean, String?> {
        var preparedStatement: PreparedStatement? = null
        var connection: Connection? = null
        return try {
            if (datasource.get() != null) {
                connection = datasource.get().connection
                preparedStatement = connection.prepareStatement("select 1")
                preparedStatement.execute()
                Pair(true, null)
            } else {
                Pair(false, "Datasource not created yet")
            }
        } catch (e: Exception) {
            logger.error(e) { "DB connection issue" }
            Pair(false, e.message)
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    override fun doHealthCheck(): HealthCheckResult {

        val dbConnectionStatus = getDbConnectionStatus()
        return HealthCheckResult(
            description = "PostgreSQL connection",
            healthy = dbConnectionStatus.first,
            details = mapOf(
                "activeConnections" to (datasource.get()?.hikariPoolMXBean?.activeConnections?.toString() ?: "0"),
                "idleConnections" to (datasource.get()?.hikariPoolMXBean?.idleConnections?.toString() ?: "0"),
                "totalConnections" to (datasource.get()?.hikariPoolMXBean?.totalConnections?.toString() ?: "0"),
            ),
            unhealthyReasons = listOfNotNull(
                if (!dbConnectionStatus.first) "DB connection issue, reason: ${dbConnectionStatus.second}" else null
            ),
            healthCheckClass = this.javaClass
        )
    }
}
