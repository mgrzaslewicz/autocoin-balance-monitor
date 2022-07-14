package autocoin.balance.wallet.summary

import autocoin.balance.price.CurrencyPrice
import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserBlockChainWallet
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.blockchain.UserBlockChainWalletService
import autocoin.balance.wallet.currency.UserCurrencyAsset
import autocoin.balance.wallet.currency.UserCurrencyAssetService
import autocoin.balance.wallet.currency.UserCurrencyAssetWithValue
import autocoin.balance.wallet.exchange.UserExchangeWallet
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWalletService
import com.google.common.util.concurrent.MoreExecutors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
class DefaultUserBalanceSummaryServiceTest {

    @Mock
    private lateinit var userBlockChainWalletRepository: UserBlockChainWalletRepository

    @Mock
    private lateinit var userExchangeWalletRepository: UserExchangeWalletRepository

    @Mock
    private lateinit var userBalanceSummaryRepository: UserBalanceSummaryRepository

    @Mock
    private lateinit var userCurrencyAssetService: UserCurrencyAssetService

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var userBlockChainWalletService: UserBlockChainWalletService

    @Mock
    private lateinit var userExchangeWalletService: UserExchangeWalletService

    private lateinit var tested: UserBalanceSummaryService

    @BeforeEach
    fun setup() {
        tested = DefaultUserBalanceSummaryService(
            userExchangeWalletRepository = { userExchangeWalletRepository },
            userBalanceSummaryRepository = { userBalanceSummaryRepository },
            userBlockChainWalletRepository = { userBlockChainWalletRepository },
            userCurrencyAssetService = userCurrencyAssetService,
            priceService = priceService,
            userBlockChainWalletService = userBlockChainWalletService,
            userExchangeWalletService = userExchangeWalletService,
            executorService = MoreExecutors.newDirectExecutorService(),
        )
    }

    @Test
    fun shouldRefreshBalances() {
        // given
        whenever(userBalanceSummaryRepository.findUniqueUserCurrencies("user1")).thenReturn(emptyList())
        // when
        tested.refreshBalanceSummary("user1")
        // then
        verify(userBlockChainWalletService).refreshWalletBalances("user1")
        verify(userExchangeWalletService).refreshWalletBalances("user1")
    }

    @Test
    fun shouldGetCurrencyBalanceSummary() {
        val userAccountId = UUID.randomUUID().toString()
        whenever(userBalanceSummaryRepository.findUniqueUserCurrencies(userAccountId)).thenReturn(listOf("BTC", "ETH", "LTC", "XYZ"))
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
        val currencyAssetLtc = UserCurrencyAssetWithValue(
            userCurrencyAsset = UserCurrencyAsset(
                userAccountId = userAccountId,
                balance = BigDecimal("13.53"),
                currency = "LTC",
                description = null,
            ),
            valueInOtherCurrency = mapOf("USD" to BigDecimal("20"))
        )
        val currencyAssetXyz = UserCurrencyAssetWithValue(
            userCurrencyAsset = UserCurrencyAsset(
                userAccountId = userAccountId,
                balance = BigDecimal("0.031"),
                currency = "XYZ",
                description = null,
            ),
            valueInOtherCurrency = mapOf("USD" to BigDecimal("30"))
        )
        whenever(userCurrencyAssetService.getUserCurrencyAssets(userAccountId)).thenReturn(listOf(currencyAssetLtc, currencyAssetXyz))
        whenever(priceService.getUsdPrice("ETH")).thenReturn(
            CurrencyPrice(
                price = BigDecimal("1400.2"),
                baseCurrency = "ETH",
                counterCurrency = "USD",
                timestampMillis = 0L,
            )
        )
        whenever(priceService.getUsdPrice("BTC")).thenReturn(
            CurrencyPrice(
                price = BigDecimal("22500.1"),
                baseCurrency = "BTC",
                counterCurrency = "USD",
                timestampMillis = 0L,
            )

        )
        whenever(priceService.getUsdValue("ETH", "15.6".toBigDecimal())).thenReturn("50000".toBigDecimal())
        whenever(priceService.getUsdValue("LTC", "332.23".toBigDecimal())).thenReturn("200".toBigDecimal())
        whenever(priceService.getUsdValue("LTC", "0.5".toBigDecimal())).thenReturn("1".toBigDecimal())
        whenever(priceService.getUsdValue("LTC", "218.15".toBigDecimal())).thenReturn("100".toBigDecimal())
        whenever(priceService.getUsdValue("LTC", "100.05".toBigDecimal())).thenReturn("50".toBigDecimal())
        whenever(priceService.getUsdValue("XYZ", "0.031".toBigDecimal())).thenReturn("30".toBigDecimal())
        // when
        val currencyBalanceSummaryList = tested.getCurrencyBalanceSummary(userAccountId)
        // then
        assertThat(currencyBalanceSummaryList).containsOnly(
            CurrencyBalanceSummary(
                currency = "ETH",
                balance = "15.6".toBigDecimal(),
                valueInOtherCurrency = mapOf("USD" to "50000".toBigDecimal()),
                priceInOtherCurrency = mapOf("USD" to "1400.2".toBigDecimal()),
                exchanges = emptyList(),
                wallets = listOf(
                    BlockchainWalletCurrencySummary(
                        walletAddress = "eth-wallet-1",
                        balance = "15.6".toBigDecimal(),
                        valueInOtherCurrency = mapOf("USD" to "50000".toBigDecimal())
                    ),
                ),
                currencyAssets = emptyList(),
            ),
            CurrencyBalanceSummary(
                currency = "BTC",
                balance = null,
                valueInOtherCurrency = mapOf("USD" to null),
                priceInOtherCurrency = mapOf("USD" to "22500.1".toBigDecimal()),
                exchanges = emptyList(),
                wallets = listOf(
                    BlockchainWalletCurrencySummary(
                        walletAddress = "btc-wallet-1",
                        balance = null,
                        valueInOtherCurrency = mapOf("USD" to null)
                    ),
                ),
                currencyAssets = emptyList(),
            ),
            CurrencyBalanceSummary(
                currency = "LTC",
                balance = "332.23".toBigDecimal(),
                valueInOtherCurrency = mapOf("USD" to 200.toBigDecimal()),
                priceInOtherCurrency = mapOf("USD" to null),
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
                currencyAssets = listOf(currencyAssetLtc),
            ),
            CurrencyBalanceSummary(
                currency = "XYZ",
                balance = "0.031".toBigDecimal(),
                valueInOtherCurrency = mapOf("USD" to 30.toBigDecimal()),
                priceInOtherCurrency = mapOf("USD" to null),
                exchanges = emptyList(),
                wallets = emptyList(),
                currencyAssets = listOf(currencyAssetXyz),
            ),
        )
    }
}

