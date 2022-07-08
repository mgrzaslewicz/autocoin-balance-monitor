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
            logger.info { "Running DB migrations" }
            appContext.createAndSetDbConnection()
            appContext.liquibase.update(Contexts())

            logger.info { "Scheduling jobs" }
            healthMetricsScheduler.scheduleSendingMetrics()

            logger.info { "Starting server" }
            server.start()
            return StartedApp(
                serverAddress = server.listenerInfo[0].address
            )
        }
    }

}
