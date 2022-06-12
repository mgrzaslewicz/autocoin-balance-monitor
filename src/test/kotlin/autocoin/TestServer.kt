package autocoin

import autocoin.balance.api.ApiController
import autocoin.balance.api.ServerBuilder
import com.nhaarman.mockitokotlin2.mock
import io.undertow.Undertow
import me.alexpanov.net.FreePortFinder

data class StartedServer(
    val port: Int,
    private val server: Undertow
) {
    fun stop() {
        server.stop()
    }
}

class TestServer {
    companion object {
        private fun getFreePort() = FreePortFinder.findFreeLocalPort()
        fun startTestServer(apiController: ApiController): StartedServer {
            val port = getFreePort()
            val serverBuilder = ServerBuilder(
                appServerPort = port,
                apiControllers = listOf(apiController),
                metricsService = mock(),
            )
            val server = serverBuilder.build()
            server.start()
            return StartedServer(
                port = port,
                server = server
            )
        }
    }
}
