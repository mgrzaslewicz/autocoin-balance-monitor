package autocoin.balance.app

import liquibase.Contexts
import mu.KLogging
import java.net.SocketAddress


class StartedApp(
    val serverAddress: SocketAddress
)

class AppStarter(private val appContext: AppContext) {
    private companion object : KLogging()

    fun start(): StartedApp {

        with(appContext) {
            initDbRelatedServices()
            logger.info { "Running DB migrations" }
            liquibase.get().update(Contexts())

            logger.info { "Restoring prices" }
            priceService.populateCache(filePriceRepository.getPrices())

            logger.info { "Scheduling jobs" }
            healthMetricsScheduler.scheduleSendingMetrics()
            priceRefreshScheduler.scheduleRefreshingPrices()

            logger.info { "Starting server" }
            server.start()
            return StartedApp(
                serverAddress = server.listenerInfo[0].address
            )
        }
    }

}
