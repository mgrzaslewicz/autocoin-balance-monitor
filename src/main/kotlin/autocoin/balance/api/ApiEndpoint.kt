package autocoin.balance.api

import io.undertow.server.HttpHandler
import io.undertow.util.HttpString

interface ApiEndpoint {
    val method: HttpString
    val urlTemplate: String
    val httpHandler: HttpHandler
}
