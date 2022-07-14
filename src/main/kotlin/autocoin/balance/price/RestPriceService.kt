package autocoin.balance.price

import autocoin.balance.metrics.MetricsService
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal


data class CurrencyPriceDto(
    val price: String,
    val baseCurrency: String,
    val counterCurrency: String,
    val timestampMillis: Long,
) {
    fun toCurrencyPrice(): CurrencyPrice {
        return CurrencyPrice(
            price = BigDecimal(price),
            baseCurrency = baseCurrency,
            counterCurrency = counterCurrency,
            timestampMillis = timestampMillis
        )
    }
}

data class CurrencyPrice(
    val price: BigDecimal,
    val baseCurrency: String,
    val counterCurrency: String,
    val timestampMillis: Long,
)

interface PriceService {
    fun getUsdPrice(currencyCode: String): CurrencyPrice? = getPrice(currencyCode, counterCurrency = "USD")
    fun getUsdValue(currencyCode: String, amount: BigDecimal): BigDecimal? = getPrice(currencyCode, counterCurrency = "USD")?.price?.multiply(amount)

    fun getPrice(baseCurrency: String, counterCurrency: String): CurrencyPrice?
    fun getValue(baseCurrency: String, counterCurrency: String, baseCurrencyAmount: BigDecimal): BigDecimal? =
        getPrice(baseCurrency, counterCurrency)?.price?.multiply(baseCurrencyAmount)
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

    override fun getPrice(baseCurrency: String, counterCurrency: String): CurrencyPrice? {
        return if (baseCurrency == counterCurrency) {
            return CurrencyPrice(
                price = BigDecimal.ONE,
                baseCurrency = baseCurrency,
                counterCurrency = counterCurrency,
                timestampMillis = currentTimeMillisFunction(),
            )
        } else {
            tryFetchPrice(baseCurrency, counterCurrency)
        }
    }

    private fun tryFetchPrice(baseCurrency: String, counterCurrency: String): CurrencyPrice? {
        return try {
            fetchPrice(baseCurrency, counterCurrency)
        } catch (e: Exception) {
            logger.error(e) { "Could not get $baseCurrency/$counterCurrency price" }
            null
        }
    }

    private fun fetchPrice(baseCurrency: String, counterCurrency: String): CurrencyPrice {
        val currencyPair = "$baseCurrency/$counterCurrency"
        logger.info { "[$currencyPair] Fetching price" }
        val millisBefore = currentTimeMillisFunction()
        val request = Request.Builder()
            .url("$priceApiUrl/prices/$counterCurrency?currencyCodes=${baseCurrency}")
            .get()
            .build()
        val priceResponse = httpClient.newCall(request).execute()
        priceResponse.use {
            metricsService.recordFetchPriceTime(currentTimeMillisFunction() - millisBefore, "currencyCode=$baseCurrency,statusCode=${priceResponse.code}")
            if (!priceResponse.isSuccessful) {
                throw PriceResponseException("[$currencyPair] Could not get price, status=${priceResponse.code}, body=${priceResponse.body?.string()}, headers=${priceResponse.headers}")
            }
            val priceDto = objectMapper.readValue(priceResponse.body?.string(), Array<CurrencyPriceDto>::class.java)
            if (priceDto.size != 1) {
                throw PriceResponseException("[$currencyPair] No expected price in response body")
            }
            return priceDto.first().toCurrencyPrice()
        }

    }

}
