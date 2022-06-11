package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.health.HealthService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.util.Methods.GET


class HealthController(
    private val healthService: HealthService,
    private val objectMapper: ObjectMapper,
) : ApiController {
    private fun getHealth() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/health"

        override val httpHandler = HttpHandler { httpServerExchange ->
            httpServerExchange.responseSender.send(objectMapper.writeValueAsString(healthService.getHealth()))
        }
    }

    override fun apiHandlers(): List<ApiEndpoint> = listOf(getHealth())
}
