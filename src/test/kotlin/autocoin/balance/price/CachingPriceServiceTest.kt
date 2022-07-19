package autocoin.balance.price

import autocoin.balance.eventbus.EventBus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Duration
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class CachingPriceServiceTest {
    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var eventBus: EventBus

    private lateinit var tested: CachingPriceService

    @BeforeEach
    fun setup() {
        tested = CachingPriceService(decorated = priceService, eventBus = eventBus)
    }

    @Test
    fun shouldGetPriceFromCache() {
        // given
        val expectedPrice: CurrencyPrice = mock()
        whenever(priceService.getPrice("A", "B")).thenReturn(expectedPrice)
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
        val expectedPrice1: CurrencyPrice = mock()
        val expectedPrice2: CurrencyPrice = mock()
        whenever(priceService.getPrice("A", "B")).thenReturn(expectedPrice1, expectedPrice2)
        val cacheDuration = Duration.of(10, ChronoUnit.MILLIS)
        val tested = CachingPriceService(
            decorated = priceService,
            maxPriceCacheAgeNanos = cacheDuration.toNanos(),
            eventBus = eventBus,
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
            maxPriceCacheNullValueAgeNanos = nullCacheDuration.toNanos(),
            eventBus = eventBus,
        )
        // when
        tested.getPrice("A", "B")
        Thread.sleep(nullCacheDuration.toMillis() + 1)
        val price = tested.getPrice("A", "B")
        // then
        assertThat(price).isNull()
        verify(priceService, times(2)).getPrice("A", "B")
    }

    @Test
    fun shouldRefreshPrices() {
        // given
        val expectedPrice: CurrencyPrice = mock()
        whenever(priceService.getUsdPrice("A")).thenReturn(expectedPrice)
        tested.refreshUsdPrices(listOf("A"))
        verify(priceService, times(1)).getUsdPrice("A")
        // when
        val actualPrice = tested.getPrice("A", "USD")
        // then
        assertThat(actualPrice).isEqualTo(expectedPrice)
        verifyNoMoreInteractions(priceService)
    }

    @Test
    fun shouldPublishPricesUpdatedEventWhenRefreshPrices() {
        // given
        val expectedPrice: CurrencyPrice = mock()
        whenever(priceService.getUsdPrice("A")).thenReturn(expectedPrice)
        // when
        tested.refreshUsdPrices(listOf("A"))
        // then
        verify(eventBus).publish(eq(pricesUpdatedEventType), argThat { arg -> arg.contains(expectedPrice) })
    }

}
