package autocoin.balance.api

interface ApiController {
    fun apiHandlers(): List<ApiEndpoint>
}
