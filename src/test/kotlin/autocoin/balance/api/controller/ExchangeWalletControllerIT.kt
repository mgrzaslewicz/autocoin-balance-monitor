package autocoin.balance.api.controller

import autocoin.TestServer.Companion.startTestServer
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.price.CurrencyPrice
import autocoin.balance.price.PriceService
import autocoin.balance.wallet.exchange.ExchangeWalletBalancesDto
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class ExchangeWalletControllerIT {

    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()
    private val authenticatedHttpHandlerWrapper = AuthenticatedHttpHandlerWrapper()

    @Test
    fun shouldGetSampleExchangeWallets() {
        // given
        val tested = ExchangeWalletController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userExchangeWalletService = mock(),
            userExchangeWalletRepository = { mock() },
            priceService = mock<PriceService>().apply {
                whenever(this.getPrice(any(), any())).thenReturn(
                    CurrencyPrice(
                        price = BigDecimal.ONE,
                        baseCurrency = "does not matter",
                        counterCurrency = "does not matter",
                        timestampMillis = System.currentTimeMillis(),
                    )
                )
                whenever(this.getValue(any(), any(), any())).thenReturn(BigDecimal.TEN)
            },
            timeMillisProvider = mock(),
        )
        val startedServer = startTestServer(tested)
        val request = Request.Builder()
            .url(startedServer.resolveUrl("/exchange/wallets/sample"))
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        response.use { response ->
            assertThat(response.isSuccessful).isTrue
            val responseDto = objectMapper.readValue(response.body?.string(), ExchangeWalletBalancesDto::class.java)
            val allPrices = responseDto.pricesInOtherCurrencies.values.flatMap { it.values }
            val allPricesInOtherCurrencies =
                responseDto.exchangeCurrencyBalances.flatMap {
                    it.exchangeBalances.flatMap {
                        it.currencyBalances.flatMap {
                            it.valueInOtherCurrency?.map { it.value } ?: emptyList()
                        }
                    }
                }
            assertThat(responseDto.exchangeCurrencyBalances).isNotEmpty
            assertThat(allPrices).allMatch { it == "1" }
            assertThat(allPricesInOtherCurrencies).allMatch { it == "10" }
        }
        startedServer.stop()
    }
}
