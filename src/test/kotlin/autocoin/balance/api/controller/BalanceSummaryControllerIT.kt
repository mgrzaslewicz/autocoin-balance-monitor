package autocoin.balance.api.controller

import autocoin.StartedServer
import autocoin.TestServer
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.wallet.summary.BlockchainWalletCurrencySummary
import autocoin.balance.wallet.summary.CurrencyBalanceSummary
import autocoin.balance.wallet.summary.ExchangeCurrencySummary
import autocoin.balance.wallet.summary.UserBalanceSummaryService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class BalanceSummaryControllerIT {
    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()

    private lateinit var startedServer: StartedServer
    private val authenticatedHttpHandlerWrapper = AuthenticatedHttpHandlerWrapper()
    private val userAccountId = authenticatedHttpHandlerWrapper.userAccountId

    @BeforeEach
    fun setup() {

    }

    @AfterEach
    fun cleanup() {
        startedServer.stop()
    }

    @Test
    fun shouldGetBalanceSummary() {
        // given
        val userBalanceSummaryService = mock<UserBalanceSummaryService>().apply {
            whenever(this.getCurrencyBalanceSummary(userAccountId)).thenReturn(
                listOf(
                    CurrencyBalanceSummary(
                        currency = "ABC",
                        balance = BigDecimal.TEN,
                        valueInOtherCurrency = mapOf("EUR" to BigDecimal.ONE),
                        exchanges = listOf(
                            ExchangeCurrencySummary(
                                exchangeName = "bittrex",
                                balance = "6.0".toBigDecimal(),
                                valueInOtherCurrency = mapOf("EUR" to 10000.toBigDecimal()),
                            )
                        ),
                        wallets = listOf(
                            BlockchainWalletCurrencySummary(
                                walletAddress = "wallet-1",
                                balance = "4.0".toBigDecimal(),
                                valueInOtherCurrency = mapOf("EUR" to 8000.toBigDecimal()),
                            )
                        ),
                    )
                )
            )
        }
        val balanceSummaryController = BalanceSummaryController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userBalanceSummaryService = userBalanceSummaryService,
        )
        startedServer = TestServer.startTestServer(balanceSummaryController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/balance/summary")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        assertThat(objectMapper.readValue(response.body?.string(), BalanceSummaryResponseDto::class.java)).isEqualTo(
            BalanceSummaryResponseDto(
                currencyBalances = listOf(
                    CurrencyBalanceSummaryDto(
                        currency = "ABC",
                        balance = "10",
                        valueInOtherCurrency = mapOf("EUR" to "1"),
                        exchanges = listOf(
                            ExchangeCurrencySummaryDto(
                                exchangeName = "bittrex",
                                balance = "6.0",
                                valueInOtherCurrency = mapOf("EUR" to "10000"),
                            )
                        ),
                        wallets = listOf(
                            BlockchainWalletCurrencySummaryDto(
                                walletAddress = "wallet-1",
                                balance = "4.0",
                                valueInOtherCurrency = mapOf("EUR" to "8000"),
                            )
                        ),
                    )
                )
            )
        )
    }
}