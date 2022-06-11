package autocoin.balance

import autocoin.balance.app.AppContext
import autocoin.balance.app.AppStarter
import autocoin.balance.app.loadConfig
import mu.KotlinLogging
import java.net.SocketAddress
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger { }

fun main(args: Array<String>) {
    var address: SocketAddress? = null
    val bootTimeMillis = measureTimeMillis {
        val config = loadConfig()
        logger.info { "Config: $config" }
        val appContext = AppContext(config)
        val appStarter = AppStarter(appContext)
        address = appStarter.start().serverAddress
    }
    logger.info { "Started in $bootTimeMillis ms, available at $address" }
}
