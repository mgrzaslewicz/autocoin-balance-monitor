package autocoin

import autocoin.balance.api.ApiController
import autocoin.balance.api.ServerBuilder
import io.undertow.Undertow
import me.alexpanov.net.FreePortFinder
import org.mockito.kotlin.mock
import java.net.URI

data class StartedServer(
    val uri: URI,
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
                uri = URI.create("http://localhost:$port"),
                server = server,
            )
        }
    }
}
