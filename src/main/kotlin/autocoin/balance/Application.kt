package autocoin.balance

import autocoin.balance.app.*
import mu.KotlinLogging
import org.testcontainers.containers.PostgreSQLContainer
import java.net.SocketAddress
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger { }

private fun startOwnDbContainer() {
    logger.warn { "Starting own database container" }
    val dbPassword = "samplePassword"
    val dbUser = "sampleUser"
    val dbContainer = PostgreSQLContainer("postgres:11.0")
    dbContainer.withUsername(dbUser)
    dbContainer.withPassword(dbPassword)
    // use in memory storage for faster execution
    dbContainer.withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))
    dbContainer.start()

    logger.warn { "Own database container started" }
    AppConfig.setJvmPropertiesForDbConnection(
        jdbcUrl = dbContainer.jdbcUrl,
        dbUsername = dbUser,
        dbPassword = dbPassword
    )

}

fun main(args: Array<String>) {
    var address: SocketAddress? = null
    val bootTimeMillis = measureTimeMillis {
        if (shouldStartOwnDbContainer) {
            startOwnDbContainer()
        }
        val config = loadConfig()
        logger.info { "Config: $config" }
        val appContext = AppContext(config)
        val appStarter = AppStarter(appContext)
        address = appStarter.start().serverAddress
    }
    logger.info { "Started in $bootTimeMillis ms, available at $address" }
}
