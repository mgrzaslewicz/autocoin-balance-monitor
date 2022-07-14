package autocoin.balance.price

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import java.math.BigDecimal
import java.time.Duration
import java.time.temporal.ChronoUnit

class CachingPriceService(
    private val decorated: PriceService,
    private val maxPriceCacheAgeNanos: Long = Long.MAX_VALUE,
    private val maxPriceCacheNullValueAgeNanos: Long = Duration.of(1, ChronoUnit.MINUTES).toNanos(),
) : PriceService {

    private val nullValueMarker = CurrencyPrice(price = BigDecimal.ZERO, baseCurrency = "", counterCurrency = "", timestampMillis = 0)

    /**
     * When getting price failed, keep null value cached - but only for a short period of time
     */
    private val priceCache: Cache<String, CurrencyPrice> = Caffeine.newBuilder()
        .expireAfter(object : Expiry<String, CurrencyPrice> {

            override fun expireAfterCreate(key: String, value: CurrencyPrice, currentTime: Long): Long {
                return if (value === nullValueMarker) {
                    maxPriceCacheNullValueAgeNanos
                } else {
                    maxPriceCacheAgeNanos
                }
            }

            override fun expireAfterUpdate(key: String, value: CurrencyPrice, currentTime: Long, currentDuration: Long): Long {
                return currentDuration
            }

            override fun expireAfterRead(key: String, value: CurrencyPrice, currentTime: Long, currentDuration: Long): Long {
                return currentDuration
            }

        })
        .build()

    private fun String.toUsdPriceCacheKey() = "$this/USD"

    override fun getPrice(baseCurrency: String, counterCurrency: String): CurrencyPrice? {
        val cacheKey = "$baseCurrency/$counterCurrency"
        val price = priceCache.get(cacheKey) {
            decorated.getPrice(baseCurrency, counterCurrency) ?: nullValueMarker
        }
        return if (price === nullValueMarker) {
            null
        } else {
            price
        }
    }

    fun refreshUsdPrices(currencies: List<String>) {
        currencies.forEach {
            val newPrice = decorated.getUsdPrice(it)
            val priceKey = it.toUsdPriceCacheKey()
            if (newPrice != null) {
                priceCache.put(priceKey, newPrice)
            }
        }
    }

}
