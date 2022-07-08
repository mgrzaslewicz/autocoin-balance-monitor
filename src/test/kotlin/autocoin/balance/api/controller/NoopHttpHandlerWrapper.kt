package autocoin.balance.api.controller

import autocoin.balance.api.HttpHandlerWrapper
import io.undertow.server.HttpHandler

class NoopHttpHandlerWrapper : HttpHandlerWrapper {
        override fun wrap(next: HttpHandler) = next
    }
