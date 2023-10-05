package autocoin.balance

import autocoin.balance.app.AppContext
import autocoin.balance.app.AppStarter
import autocoin.balance.app.AppVersion
import autocoin.balance.app.config.ConfigLoader
import mu.KotlinLogging
import org.testcontainers.containers.PostgreSQLContainer
import java.net.SocketAddress
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger { }

private fun startOwnDbContainer(dbUser: String, dbPassword: String): String {
    logger.warn { "Starting own database container" }
    val dbContainer = PostgreSQLContainer("postgres:11.0")
    dbContainer.withUsername(dbUser)
    dbContainer.withPassword(dbPassword)
    // use in memory storage for faster execution
    dbContainer.withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
    dbContainer.start()

    logger.warn { "Own database container started" }
    return dbContainer.jdbcUrl
}

fun main(args: Array<String>) {
    logger.info { "Starting application. Version ${AppVersion().commitId ?: "unavailable"}..." }
    try {
        var address: SocketAddress? = null
        val bootTimeMillis = measureTimeMillis {
            var config = ConfigLoader.loadConfig()
            if (config.shouldStartOwnDbContainer) {
                logger.info { "Config pre db container start: $config" }
                val jdbcUrl = startOwnDbContainer(config.dbUsername, config.dbPassword)
                config = config.copy(jdbcUrl = jdbcUrl)
            }
            logger.info { "Config: $config" }
            val appContext = AppContext(config)
            val appStarter = AppStarter(appContext)
            address = appStarter.start().serverAddress
        }
        logger.info { "Started in $bootTimeMillis ms, available at $address" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to start" }
        exitProcess(1)
    }
}
