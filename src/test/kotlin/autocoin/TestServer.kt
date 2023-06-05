package autocoin

import autocoin.balance.api.ApiController
import autocoin.balance.api.ServerBuilder
import io.undertow.Undertow
import me.alexpanov.net.FreePortFinder
import org.mockito.kotlin.mock
import java.net.URI
import java.net.URL

data class StartedServer(
    private val uri: URI,
    private val server: Undertow
) {
    fun stop() {
        server.stop()
    }

    fun resolveUrl(path: String): URL = uri.resolve(path).toURL()
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
