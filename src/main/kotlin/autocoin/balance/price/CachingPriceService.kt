package autocoin.balance.price

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import java.math.BigDecimal
import java.time.Duration
import java.time.temporal.ChronoUnit

class CachingPriceService(
    private val decorated: PriceService,
    private val maxPriceCacheAgeNanos: Long = Duration.of(1, ChronoUnit.HOURS).toNanos(),
    private val maxPriceCacheNullValueAgeNanos: Long = Duration.of(1, ChronoUnit.MINUTES).toNanos(),
) : PriceService {

    private val nullValueMarker = -BigDecimal.ONE

    /**
     * When getting price failed, keep null value cached much shorter than successfully fetched price
     */
    private val priceCache: Cache<String, BigDecimal> = Caffeine.newBuilder()
        .expireAfter(object : Expiry<String, BigDecimal> {

            override fun expireAfterCreate(key: String, value: BigDecimal, currentTime: Long): Long {
                return if (value === nullValueMarker) {
                    maxPriceCacheNullValueAgeNanos
                } else {
                    maxPriceCacheAgeNanos
                }
            }

            override fun expireAfterUpdate(key: String, value: BigDecimal, currentTime: Long, currentDuration: Long): Long {
                return currentDuration
            }

            override fun expireAfterRead(key: String, value: BigDecimal, currentTime: Long, currentDuration: Long): Long {
                return currentDuration
            }

        })
        .build()

    private fun String.toUsdPriceCacheKey() = "$this/USD"

    override fun getPrice(baseCurrency: String, counterCurrency: String): BigDecimal? {
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
            priceCache.invalidate(it.toUsdPriceCacheKey())
            getUsdPrice(it)
        }
    }

}
