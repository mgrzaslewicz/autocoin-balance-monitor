package autocoin.balance.api.controller

import autocoin.TestServer.Companion.startTestServer
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.wallet.exchange.ExchangeWalletBalancesDto
import automate.profit.autocoin.exchange.time.SystemTimeMillisProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

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
            priceService = mock(),
            timeMillisProvider = SystemTimeMillisProvider(),
        )
        val startedServer = startTestServer(tested)
        val request = Request.Builder().url(startedServer.resolveUrl("/exchange/wallets?sampleBalance=true"))
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        response.use {
            assertThat(response.isSuccessful).isTrue
            val responseDto = objectMapper.readValue(it.body?.string(), ExchangeWalletBalancesDto::class.java)
            assertThat(responseDto.exchangeCurrencyBalances).isNotEmpty
        }
        startedServer.stop()
    }
}
