package autocoin.balance.price

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class CachingPriceServiceTest {
    @Mock
    private lateinit var priceService: PriceService

    @Test
    fun shouldGetPriceFromCache() {
        // given
        val expectedPrice = BigDecimal.ONE
        whenever(priceService.getPrice("A", "B")).thenReturn(expectedPrice)
        val tested = CachingPriceService(decorated = priceService)
        // when
        val price = tested.getPrice("A", "B")
        tested.getPrice("A", "B")
        // then
        assertThat(price).isEqualTo(expectedPrice)
        verify(priceService, times(1)).getPrice("A", "B")
    }

    @Test
    fun shouldGetPriceAgainAfterExpiry() {
        // given
        val expectedPrice1 = BigDecimal.ONE
        val expectedPrice2 = BigDecimal.TEN
        whenever(priceService.getPrice("A", "B")).thenReturn(expectedPrice1, expectedPrice2)
        val cacheDuration = Duration.of(10, ChronoUnit.MILLIS)
        val tested = CachingPriceService(
            decorated = priceService,
            maxPriceCacheAgeNanos = cacheDuration.toNanos()
        )
        // when
        tested.getPrice("A", "B")
        Thread.sleep(cacheDuration.toMillis() + 1)
        val price = tested.getPrice("A", "B")
        // then
        assertThat(price).isEqualTo(expectedPrice2)
        verify(priceService, times(2)).getPrice("A", "B")
    }

    @Test
    fun shouldGetNullPriceFromCache() {
        // given
        val tested = CachingPriceService(decorated = priceService)
        // when
        val price = tested.getPrice("A", "B")
        tested.getPrice("A", "B")
        // then
        assertThat(price).isNull()
        verify(priceService, times(1)).getPrice("A", "B")
    }

    @Test
    fun shouldGetNullPriceAgainAfterExpiry() {
        // given
        val nullCacheDuration = Duration.of(10, ChronoUnit.MILLIS)
        val tested = CachingPriceService(
            decorated = priceService,
            maxPriceCacheNullValueAgeNanos = nullCacheDuration.toNanos()
        )
        // when
        tested.getPrice("A", "B")
        Thread.sleep(nullCacheDuration.toMillis() + 1)
        val price = tested.getPrice("A", "B")
        // then
        assertThat(price).isNull()
        verify(priceService, times(2)).getPrice("A", "B")
    }

}