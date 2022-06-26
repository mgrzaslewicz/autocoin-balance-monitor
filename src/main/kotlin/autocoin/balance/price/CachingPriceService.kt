package autocoin.balance.price

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import com.github.benmanes.caffeine.cache.LoadingCache
import java.math.BigDecimal
import java.time.Duration
import java.time.temporal.ChronoUnit

class CachingPriceService(
    private val decorated: PriceService,
    private val maxPriceCacheAgeNanos: Long = Duration.of(24, ChronoUnit.HOURS).toNanos(),
    private val maxPriceCacheNullValueAgeNanos: Long = Duration.of(1, ChronoUnit.HOURS).toNanos(),
) : PriceService {

    private val nullValueMarker = -BigDecimal.ONE

    /**
     * When getting price failed, keep null value cached much shorter than successfully fetched price
     */
    private val priceCache: LoadingCache<String, BigDecimal> = Caffeine.newBuilder()
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
        .refreshAfterWrite(Duration.of(1, ChronoUnit.HOURS))
        .build { decorated.getUsdPriceOrNull(it.toUsdPriceCacheKey()) ?: nullValueMarker }

    private fun String.toUsdPriceCacheKey() = "$this/USD"

    override fun getUsdPrice(currencyCode: String): BigDecimal {
        return priceCache.get(currencyCode.toUsdPriceCacheKey()) {
            decorated.getUsdPrice(currencyCode)
        }
    }

    override fun getUsdPriceOrNull(currencyCode: String): BigDecimal? {
        val usdPrice = priceCache.get(currencyCode.toUsdPriceCacheKey()) {
            decorated.getUsdPriceOrNull(currencyCode) ?: nullValueMarker
        }
        return if (usdPrice === nullValueMarker) {
            null
        } else {
            usdPrice
        }
    }

    override fun getUsdValue(currencyCode: String, amount: BigDecimal): BigDecimal {
        val usdPrice = getUsdPrice(currencyCode)
        return amount.multiply(usdPrice)
    }

    override fun getUsdValueOrNull(currencyCode: String, amount: BigDecimal): BigDecimal? {
        val usdPrice = getUsdPriceOrNull(currencyCode)
        return if (usdPrice == null) {
            null
        } else {
            amount.multiply(usdPrice)
        }
    }

}
