package autocoin.balance.health

import mu.KLogging
import java.sql.Connection
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

data class Health(
    val healthy: Boolean,
    val connectedToDb: Boolean,
    val unhealthyReasons: List<String>,
)

class HealthService(private val datasource: AtomicReference<DataSource>) {

    private companion object : KLogging()

    private fun isDbConnectionAlive(): Boolean {
        return try {
            if (datasource.get() != null) {
                datasource.get().connection.prepareStatement("select 1").execute()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error(e) { "DB connection issue" }
            false
        }
    }

    fun getHealth(): Health {
        val connectedToDb = isDbConnectionAlive()
        val health = Health(
            healthy = connectedToDb,
            connectedToDb = connectedToDb,
            unhealthyReasons = listOfNotNull(
                if (!connectedToDb) "DB connection issue" else null
            )
        )
        return health
    }
}
