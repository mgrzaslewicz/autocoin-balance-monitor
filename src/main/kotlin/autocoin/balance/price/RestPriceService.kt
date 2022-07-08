package autocoin.balance.price

import autocoin.balance.metrics.MetricsService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
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
    fun getUsdPriceOrNull(currencyCode: String): BigDecimal?
    fun getUsdValue(currencyCode: String, amount: BigDecimal): BigDecimal
    fun getUsdValueOrNull(currencyCode: String, amount: BigDecimal): BigDecimal?
}

class PriceResponseException(message: String) : IllegalStateException(message)

class RestPriceService(
    private val priceApiUrl: String,
    private val httpClient: OkHttpClient,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService,
    private val currentTimeMillisFunction: () -> Long = System::currentTimeMillis,
) : PriceService {

    private companion object : KLogging()

    override fun getUsdPrice(currencyCode: String): BigDecimal {
        if (currencyCode == "USD") {
            return BigDecimal.ONE
        }
        return fetchUsdPrice(currencyCode)
    }

    override fun getUsdPriceOrNull(currencyCode: String): BigDecimal? {
        return try {
            getUsdPrice(currencyCode)
        } catch (e: Exception) {
            logger.error(e) { "[$currencyCode/USD] Could not get price" }
            null
        }
    }

    override fun getUsdValue(currencyCode: String, amount: BigDecimal): BigDecimal {
        val price = getUsdPrice(currencyCode)
        return amount.multiply(price)
    }

    override fun getUsdValueOrNull(currencyCode: String, amount: BigDecimal): BigDecimal? {
        return try {
            getUsdValue(currencyCode, amount)
        } catch (e: Exception) {
            logger.error(e) { "[$currencyCode/USD] Could not get price" }
            null
        }
    }

    private fun fetchUsdPrice(currencyCode: String): BigDecimal {
        logger.info { "[$currencyCode/USD] Fetching price" }
        val millisBefore = currentTimeMillisFunction()
        val request = Request.Builder()
            .url("$priceApiUrl/prices/USD?currencyCodes=${currencyCode}")
            .get()
            .build()
        val priceResponse = httpClient.newCall(request).execute()
        priceResponse.use {
            metricsService.recordFetchPriceTime(currentTimeMillisFunction() - millisBefore, "currencyCode=$currencyCode,statusCode=${priceResponse.code}")
            if (!priceResponse.isSuccessful) {
                throw PriceResponseException("[$currencyCode/USD] Could not get price, status=${priceResponse.code}, body=${priceResponse.body?.string()}, headers=${priceResponse.headers}")
            }
            val priceDto = objectMapper.readValue(priceResponse.body?.string(), Array<CurrencyPriceDto>::class.java)
            if (priceDto.size != 1) {
                throw PriceResponseException("[$currencyCode/USD] No expected price in response body")
            }
            return priceDto.first().price.toBigDecimal()
        }

    }

}
