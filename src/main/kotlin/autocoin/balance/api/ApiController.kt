package autocoin.balance.api

interface ApiController {
    fun apiEndpoints(): List<ApiEndpoint>
}
