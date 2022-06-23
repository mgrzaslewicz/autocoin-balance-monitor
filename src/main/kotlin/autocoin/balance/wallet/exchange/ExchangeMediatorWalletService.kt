package autocoin.balance.wallet.exchange

import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalancesDto
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
import okhttp3.OkHttpClient
import okhttp3.Request

interface ExchangeMediatorWalletService {
    fun getExchangeUserBalances(userAccountId: String): List<ExchangeCurrencyBalancesDto>
}

class RestExchangeMediatorWalletService(
    private val exchangeMediatorApiUrl: String,
    private val httpClient: OkHttpClient,
    private val objectMapper: ObjectMapper,
) : ExchangeMediatorWalletService {
    private companion object : KLogging()

    override fun getExchangeUserBalances(userAccountId: String): List<ExchangeCurrencyBalancesDto> {
        val request = Request.Builder().get().url("$exchangeMediatorApiUrl/wallet/currency-balances/user/$userAccountId").build()
        return try {
            val response = httpClient.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    val result = objectMapper.readValue(it.body?.string(), Array<ExchangeCurrencyBalancesDto>::class.java).toList()
                    result
                } else {
                    logger.error { "Could not get exchange user balances for userAccountId=$userAccountId. Status code=${it.code}, body=${it.body?.string()}, headers=${it.headers}" }
                    emptyList()
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Could not get exchange user balances for userAccountId=$userAccountId" }
            emptyList()
        }
    }

}

