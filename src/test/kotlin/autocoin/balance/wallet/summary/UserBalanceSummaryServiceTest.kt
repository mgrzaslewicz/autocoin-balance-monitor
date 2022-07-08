package autocoin.balance.wallet.summary

import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserBlockChainWallet
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWallet
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
class UserBalanceSummaryServiceTest {

    @Mock
    private lateinit var userBlockChainWalletRepository: UserBlockChainWalletRepository

    @Mock
    private lateinit var userExchangeWalletRepository: UserExchangeWalletRepository

    @Mock
    private lateinit var userBalanceSummaryRepository: UserBalanceSummaryRepository

    @Mock
    private lateinit var priceService: PriceService

    private lateinit var tested: UserBalanceSummaryService

    @BeforeEach
    fun setup() {
        tested = UserBalanceSummaryService(
            userExchangeWalletRepository = { userExchangeWalletRepository },
            userBalanceSummaryRepository = { userBalanceSummaryRepository },
            userBlockChainWalletRepository = { userBlockChainWalletRepository },
            priceService = priceService,
        )
    }

    @Test
    fun shouldGetCurrencyBalanceSummary() {
        val userAccountId = UUID.randomUUID().toString()
        whenever(userBalanceSummaryRepository.findUniqueUserCurrencies(userAccountId)).thenReturn(listOf("BTC", "ETH", "LTC"))
        whenever(userBlockChainWalletRepository.findManyByUserAccountIdAndCurrency(userAccountId, "BTC"))
            .thenReturn(
                listOf(
                    UserBlockChainWallet(
                        userAccountId = userAccountId,
                        balance = null,
                        currency = "BTC",
                        description = null,
                        walletAddress = "btc-wallet-1",
                    )
                )
            )
        whenever(userBlockChainWalletRepository.findManyByUserAccountIdAndCurrency(userAccountId, "ETH"))
            .thenReturn(
                listOf(
                    UserBlockChainWallet(
                        userAccountId = userAccountId,
                        balance = "15.6".toBigDecimal(),
                        currency = "ETH",
                        description = null,
                        walletAddress = "eth-wallet-1",
                    )
                )
            )
        whenever(userBlockChainWalletRepository.findManyByUserAccountIdAndCurrency(userAccountId, "LTC"))
            .thenReturn(
                listOf(
                    UserBlockChainWallet(
                        userAccountId = userAccountId,
                        balance = "0.5".toBigDecimal(),
                        currency = "LTC",
                        description = null,
                        walletAddress = "ltc-wallet-1",
                    )
                )
            )
        whenever(userExchangeWalletRepository.findManyByUserAccountIdAndCurrency(userAccountId, "BTC")).thenReturn(emptyList())
        whenever(userExchangeWalletRepository.findManyByUserAccountIdAndCurrency(userAccountId, "ETH")).thenReturn(emptyList())
        whenever(userExchangeWalletRepository.findManyByUserAccountIdAndCurrency(userAccountId, "LTC"))
            .thenReturn(
                listOf(
                    UserExchangeWallet(
                        userAccountId = userAccountId,
                        balance = 218.15.toBigDecimal(),
                        currency = "LTC",
                        exchangeUserId = UUID.randomUUID().toString(),
                        exchange = "bittrex",
                        amountInOrders = BigDecimal.ZERO,
                        amountAvailable = 218.15.toBigDecimal(),
                    ),
                    UserExchangeWallet(
                        userAccountId = userAccountId,
                        balance = 100.05.toBigDecimal(),
                        currency = "LTC",
                        exchangeUserId = UUID.randomUUID().toString(),
                        exchange = "binance",
                        amountInOrders = BigDecimal.ZERO,
                        amountAvailable = 100.05.toBigDecimal(),
                    )
                )
            )
        whenever(priceService.getUsdValueOrNull("ETH", "15.6".toBigDecimal())).thenReturn("50000".toBigDecimal())
        whenever(priceService.getUsdValueOrNull("LTC", "318.70".toBigDecimal())).thenReturn("200".toBigDecimal())
        whenever(priceService.getUsdValueOrNull("LTC", "0.5".toBigDecimal())).thenReturn("1".toBigDecimal())
        whenever(priceService.getUsdValueOrNull("LTC", "218.15".toBigDecimal())).thenReturn("100".toBigDecimal())
        whenever(priceService.getUsdValueOrNull("LTC", "100.05".toBigDecimal())).thenReturn("50".toBigDecimal())
        // when
        val currencyBalanceSummaryList = tested.getCurrencyBalanceSummary(userAccountId)
        // then
        assertThat(currencyBalanceSummaryList).containsOnly(
            CurrencyBalanceSummary(
                currency = "ETH",
                balance = "15.6".toBigDecimal(),
                valueInOtherCurrency = mapOf("USD" to "50000".toBigDecimal()),
                exchanges = emptyList(),
                wallets = listOf(
                    BlockchainWalletCurrencySummary(
                        walletAddress = "eth-wallet-1",
                        balance = "15.6".toBigDecimal(),
                        valueInOtherCurrency = mapOf("USD" to "50000".toBigDecimal())
                    ),
                )
            ),
            CurrencyBalanceSummary(
                currency = "BTC",
                balance = null,
                valueInOtherCurrency = mapOf("USD" to null),
                exchanges = emptyList(),
                wallets = listOf(
                    BlockchainWalletCurrencySummary(
                        walletAddress = "btc-wallet-1",
                        balance = null,
                        valueInOtherCurrency = mapOf("USD" to null)
                    ),
                )
            ),
            CurrencyBalanceSummary(
                currency = "LTC",
                balance = "318.70".toBigDecimal(),
                valueInOtherCurrency = mapOf("USD" to 200.toBigDecimal()),
                exchanges = listOf(
                    ExchangeCurrencySummary(
                        exchangeName = "bittrex",
                        balance = 218.15.toBigDecimal(),
                        valueInOtherCurrency = mapOf("USD" to 100.toBigDecimal())
                    ),
                    ExchangeCurrencySummary(
                        exchangeName = "binance",
                        balance = 100.05.toBigDecimal(),
                        valueInOtherCurrency = mapOf("USD" to 50.toBigDecimal())
                    )
                ),
                wallets = listOf(
                    BlockchainWalletCurrencySummary(
                        walletAddress = "ltc-wallet-1",
                        balance = 0.5.toBigDecimal(),
                        valueInOtherCurrency = mapOf("USD" to 1.toBigDecimal())
                    ),
                ),
            ),
        )
    }
}

