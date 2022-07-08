package autocoin.balance.wallet.exchange

import autocoin.balance.price.PriceService
import automate.profit.autocoin.exchange.time.TimeMillisProvider
import automate.profit.autocoin.exchange.wallet.ExchangeBalanceDto
import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalanceDto
import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalancesDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*

class UserExchangeWalletServiceTest {
    private val currentTimeMillis = 102L
    private val timeMillisProvider = object : TimeMillisProvider {
        override fun now(): Long = currentTimeMillis
    }

    @Test
    fun shouldDeleteAllPreviousWalletsAndImportsWhenWhenRefreshWalletBalances() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val userExchangeWalletLastRefreshRepository = mock<UserExchangeWalletLastRefreshRepository>()
        val userExchangeWalletRepository = mock<UserExchangeWalletRepository>()
        val exchangeMediatorWalletService = mock<ExchangeMediatorWalletService>().apply {
            whenever(this.getExchangeUserBalances(userAccountId)).thenReturn(emptyList())
        }
        val tested = UserExchangeWalletService(
            userExchangeWalletRepository = { userExchangeWalletRepository },
            userExchangeWalletLastRefreshRepository = { userExchangeWalletLastRefreshRepository },
            exchangeMediatorWalletService = exchangeMediatorWalletService,
            priceService = mock(),
        )
        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(userExchangeWalletRepository).deleteByUserAccountId(userAccountId)
        verify(userExchangeWalletLastRefreshRepository).deleteByUserAccountId(userAccountId)
        verify(userExchangeWalletRepository, never()).insertWallet(any())
    }

    @Test
    fun shouldInsertExchangeWalletsWhenRefreshWalletBalances() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val userExchangeWalletLastRefreshRepository = mock<UserExchangeWalletLastRefreshRepository>()
        val userExchangeWalletRepository = mock<UserExchangeWalletRepository>()
        val exchangeMediatorWalletService = mock<ExchangeMediatorWalletService>().apply {
            whenever(this.getExchangeUserBalances(userAccountId)).thenReturn(
                listOf(
                    ExchangeCurrencyBalancesDto(
                        exchangeUserId = "exchangeUserId1",
                        exchangeUserName = "john1",
                        exchangeBalances = listOf(
                            ExchangeBalanceDto(
                                exchangeName = "bittrex",
                                currencyBalances = listOf(
                                    ExchangeCurrencyBalanceDto(
                                        currencyCode = "ETH",
                                        amountAvailable = "15.0",
                                        totalAmount = "20.0",
                                        amountInOrders = "5.0",
                                    )
                                ),
                                errorMessage = null,
                            ),
                        )
                    )
                )
            )
        }
        val tested = UserExchangeWalletService(
            userExchangeWalletLastRefreshRepository = { userExchangeWalletLastRefreshRepository },
            userExchangeWalletRepository = { userExchangeWalletRepository },
            exchangeMediatorWalletService = exchangeMediatorWalletService,
            priceService = mock(),
            timeMillisProvider = timeMillisProvider,
        )
        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(userExchangeWalletRepository).insertWallet(argThat {
            this.userAccountId == userAccountId
                    && this.exchange == "bittrex"
                    && this.exchangeUserId == "exchangeUserId1"
                    && this.currency == "ETH"
                    && this.balance == BigDecimal("20.0")
                    && this.amountInOrders == BigDecimal("5.0")
        })
        verify(userExchangeWalletLastRefreshRepository).insertWalletLastRefresh(argThat {
            this.userAccountId == userAccountId
                    && this.exchange == "bittrex"
                    && this.exchangeUserId == "exchangeUserId1"
                    && this.userAccountId == userAccountId
                    && this.insertTime == Timestamp(currentTimeMillis)
                    && this.errorMessage == null
        })
    }


    @Test
    fun shouldInsertLastWalletRefreshWhenRefreshWalletBalances() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val userExchangeWalletLastRefreshRepository = mock<UserExchangeWalletLastRefreshRepository>()
        val userExchangeWalletRepository = mock<UserExchangeWalletRepository>()
        val priceService = mock<PriceService>()
        val exchangeMediatorWalletService = mock<ExchangeMediatorWalletService>().apply {
            whenever(this.getExchangeUserBalances(userAccountId)).thenReturn(
                listOf(
                    ExchangeCurrencyBalancesDto(
                        exchangeUserId = "exchangeUserId1",
                        exchangeUserName = "john1",
                        exchangeBalances = listOf(
                            ExchangeBalanceDto(
                                exchangeName = "binance",
                                currencyBalances = emptyList(),
                                errorMessage = "API key expired",
                            ),
                        )
                    ),
                    ExchangeCurrencyBalancesDto(
                        exchangeUserId = "exchangeUserId2",
                        exchangeUserName = "john2",
                        exchangeBalances = listOf(
                            ExchangeBalanceDto(
                                exchangeName = "bittrex",
                                currencyBalances = emptyList(),
                                errorMessage = null,
                            ),
                        )
                    )
                )
            )
        }
        val tested = UserExchangeWalletService(
            userExchangeWalletLastRefreshRepository = { userExchangeWalletLastRefreshRepository },
            userExchangeWalletRepository = { userExchangeWalletRepository },
            exchangeMediatorWalletService = exchangeMediatorWalletService,
            timeMillisProvider = timeMillisProvider,
            priceService = priceService,
        )
        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(userExchangeWalletRepository, never()).insertWallet(any())
        verify(userExchangeWalletLastRefreshRepository).insertWalletLastRefresh(argThat {
            this.userAccountId == userAccountId
                    && this.exchange == "binance"
                    && this.exchangeUserId == "exchangeUserId1"
                    && this.exchangeUserName == "john1"
                    && this.errorMessage == "API key expired"
                    && this.insertTime.time == currentTimeMillis
        })
        verify(userExchangeWalletLastRefreshRepository).insertWalletLastRefresh(argThat {
            this.userAccountId == userAccountId
                    && this.exchange == "bittrex"
                    && this.exchangeUserId == "exchangeUserId2"
                    && this.exchangeUserName == "john2"
                    && this.errorMessage == null
                    && this.insertTime.time == currentTimeMillis
        })
    }


    @Test
    fun shouldGetWalletBalances() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val userExchangeWalletLastRefreshRepository = mock<UserExchangeWalletLastRefreshRepository>().apply {
            whenever(this.findManyByUserAccountId(userAccountId)).thenReturn(
                listOf(
                    UserExchangeWalletLastRefresh(
                        userAccountId = userAccountId,
                        exchange = "poloniex",
                        errorMessage = "API key expired",
                        exchangeUserId = "exchangeUserId1",
                        exchangeUserName = "john1",
                        insertTime = Timestamp(currentTimeMillis),
                    ),
                    UserExchangeWalletLastRefresh(
                        userAccountId = userAccountId,
                        exchange = "bittrex",
                        errorMessage = null,
                        exchangeUserId = "exchangeUserId1",
                        exchangeUserName = "john1",
                        insertTime = Timestamp(currentTimeMillis),
                    ),
                    UserExchangeWalletLastRefresh(
                        userAccountId = userAccountId,
                        exchange = "bittrex",
                        errorMessage = null,
                        exchangeUserId = "exchangeUserId2",
                        exchangeUserName = "john2",
                        insertTime = Timestamp(currentTimeMillis),
                    ),
                )
            )
        }
        val userExchangeWalletRepository = mock<UserExchangeWalletRepository>().apply {
            whenever(this.findManyByUserAccountId(userAccountId)).thenReturn(
                listOf(
                    UserExchangeWallet(
                        userAccountId = userAccountId,
                        exchange = "bittrex",
                        exchangeUserId = "exchangeUserId1",
                        currency = "ETH",
                        balance = 20.toBigDecimal(),
                        amountInOrders = 5.toBigDecimal(),
                        amountAvailable = 15.toBigDecimal(),
                    ),
                    UserExchangeWallet(
                        userAccountId = userAccountId,
                        exchange = "bittrex",
                        exchangeUserId = "exchangeUserId1",
                        currency = "BTC",
                        balance = 31.toBigDecimal(),
                        amountInOrders = 10.toBigDecimal(),
                        amountAvailable = 21.toBigDecimal(),
                    ),
                    UserExchangeWallet(
                        userAccountId = userAccountId,
                        exchange = "bittrex",
                        exchangeUserId = "exchangeUserId2",
                        currency = "XRP",
                        balance = 8.toBigDecimal(),
                        amountInOrders = 0.toBigDecimal(),
                        amountAvailable = 8.toBigDecimal(),
                    ),
                )
            )
        }
        val priceService = mock<PriceService>().apply {
            whenever(this.getUsdValue("ETH", "20".toBigDecimal())).thenReturn("100.0".toBigDecimal())
            whenever(this.getUsdValue("BTC", "31".toBigDecimal())).thenReturn("2000.0".toBigDecimal())
        }
        val tested = UserExchangeWalletService(
            userExchangeWalletLastRefreshRepository = { userExchangeWalletLastRefreshRepository },
            userExchangeWalletRepository = { userExchangeWalletRepository },
            exchangeMediatorWalletService = mock(),
            timeMillisProvider = timeMillisProvider,
            priceService = priceService,
        )
        // when
        val walletBalances = tested.getWalletBalances(userAccountId)
        // then
        assertThat(walletBalances).isEqualTo(
            ExchangeWalletBalancesDto(
                isShowingRealBalance = true,
                refreshTimeMillis = currentTimeMillis,
                exchangeCurrencyBalances = listOf(
                    ExchangeCurrencyBalancesDto(
                        exchangeUserId = "exchangeUserId1",
                        exchangeUserName = "john1",
                        exchangeBalances = listOf(
                            ExchangeBalanceDto(
                                exchangeName = "poloniex",
                                errorMessage = "API key expired",
                                currencyBalances = emptyList(),
                            ),
                            ExchangeBalanceDto(
                                exchangeName = "bittrex",
                                errorMessage = null,
                                currencyBalances = listOf(
                                    ExchangeCurrencyBalanceDto(
                                        currencyCode = "ETH",
                                        amountAvailable = "15",
                                        amountInOrders = "5",
                                        totalAmount = "20",
                                        valueInOtherCurrency = mapOf("USD" to "100.0"),
                                    ),
                                    ExchangeCurrencyBalanceDto(
                                        currencyCode = "BTC",
                                        amountAvailable = "21",
                                        amountInOrders = "10",
                                        totalAmount = "31",
                                        valueInOtherCurrency = mapOf("USD" to "2000.0"),
                                    )
                                )
                            ),
                        )
                    ),
                    ExchangeCurrencyBalancesDto(
                        exchangeUserId = "exchangeUserId2",
                        exchangeUserName = "john2",
                        exchangeBalances = listOf(
                            ExchangeBalanceDto(
                                exchangeName = "bittrex",
                                errorMessage = null,
                                currencyBalances = listOf(
                                    ExchangeCurrencyBalanceDto(
                                        currencyCode = "XRP",
                                        amountAvailable = "8",
                                        amountInOrders = "0",
                                        totalAmount = "8",
                                        valueInOtherCurrency = mapOf("USD" to null),
                                    )
                                )
                            ),
                        )
                    )
                )
            )
        )

    }

}
