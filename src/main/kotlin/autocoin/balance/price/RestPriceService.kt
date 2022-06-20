package autocoin.balance.price

import autocoin.balance.logger.PeriodicalLogger
import autocoin.balance.metrics.MetricsService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal


data class CurrencyPriceDto(
    val price: Double,
    val baseCurrency: String,
    val counterCurrency: String
)

interface PriceService {
    fun getUsdPrice(currencyCode: String): BigDecimal
    fun getUsdValue(currencyCode: String, amount: BigDecimal): BigDecimal
}

class PriceResponseException(message: String, val reasonTag: String) : IllegalStateException(message)

class RestPriceService(
    private val priceApiUrl: String,
    private val httpClient: OkHttpClient,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService,
    private val currentTimeMillisFunction: () -> Long = System::currentTimeMillis,
) : PriceService {

    private companion object {
        private val logger = PeriodicalLogger(wrapped = KotlinLogging.logger {}).scheduleLogFlush()
    }

    override fun getUsdPrice(currencyCode: String): BigDecimal {
        if (currencyCode == "USD") {
            return BigDecimal.ONE
        }
        return fetchUsdPrice(currencyCode)
    }

    override fun getUsdValue(currencyCode: String, amount: BigDecimal): BigDecimal {
        val price = getUsdPrice(currencyCode)
        return amount.multiply(price)
    }

    private fun fetchUsdPrice(currencyCode: String): BigDecimal {
        logger.frequentInfo { "[$currencyCode/USD] Fetching price" }
        val millisBefore = currentTimeMillisFunction()
        val request = Request.Builder()
            .url("$priceApiUrl/prices/USD?currencyCodes=${currencyCode}")
            .get()
            .build()
        val priceResponse = httpClient.newCall(request).execute()
        priceResponse.use {
            metricsService.recordFetchPriceTime(currentTimeMillisFunction() - millisBefore, "currencyCode=$currencyCode,statusCode=${priceResponse.code}")
            if (!priceResponse.isSuccessful) {
                throw PriceResponseException(reasonTag = "request-error", message = "[$currencyCode/USD] Could not get price, response error code=${priceResponse.code}")
            }
            try {
                val priceDto = objectMapper.readValue(priceResponse.body?.string(), Array<CurrencyPriceDto>::class.java)
                if (priceDto.size != 1) {
                    throw PriceResponseException(reasonTag = "missing-price", message = "[$currencyCode/USD] No expected price in response body")
                }
                return priceDto.first().price.toBigDecimal()
            } catch (e: Exception) {
                logger.error(e) {"[$currencyCode/USD] Could not parse response body"}
                val errorMessage = "[$currencyCode/USD] Could not parse response body. Exception=${e.message}"
                throw PriceResponseException(message = errorMessage, reasonTag = "response-parse-error")
            }
        }
    }

}