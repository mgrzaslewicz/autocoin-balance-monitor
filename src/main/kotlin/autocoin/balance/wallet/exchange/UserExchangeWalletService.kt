package autocoin.balance.wallet.exchange

import autocoin.balance.price.PriceService
import automate.profit.autocoin.exchange.time.SystemTimeMillisProvider
import automate.profit.autocoin.exchange.time.TimeMillisProvider
import automate.profit.autocoin.exchange.wallet.ExchangeBalanceDto
import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalanceDto
import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalancesDto
import mu.KLogging
import java.sql.Timestamp
import java.util.*


data class ExchangeWalletBalancesDto(
    val refreshTimeMillis: Long?,
    val isShowingRealBalance: Boolean,
    val pricesInOtherCurrencies: Map<String, Map<String, String?>>,
    val exchangeCurrencyBalances: List<ExchangeCurrencyBalancesDto>,
)

class UserExchangeWalletService(
    private val userExchangeWalletRepository: () -> UserExchangeWalletRepository,
    private val userExchangeWalletLastRefreshRepository: () -> UserExchangeWalletLastRefreshRepository,
    private val exchangeMediatorWalletService: ExchangeMediatorWalletService,
    private val priceService: PriceService,
    private val timeMillisProvider: TimeMillisProvider = SystemTimeMillisProvider(),
) {
    private companion object : KLogging()

    fun refreshWalletBalances(userAccountId: String) {
        val exchangeUserBalances = exchangeMediatorWalletService.getExchangeUserBalances(userAccountId)
        val userExchangeWalletRepository = userExchangeWalletRepository()
        val userExchangeWalletLastRefreshRepository = userExchangeWalletLastRefreshRepository()
        userExchangeWalletRepository.deleteByUserAccountId(userAccountId)
        userExchangeWalletLastRefreshRepository.deleteByUserAccountId(userAccountId)
        val currentTimeMillis = timeMillisProvider.now()
        exchangeUserBalances.forEach { currencyBalances ->
            currencyBalances.exchangeBalances.forEach { exchangeBalance ->
                userExchangeWalletLastRefreshRepository.insertWalletLastRefresh(
                    UserExchangeWalletLastRefresh(
                        userAccountId = userAccountId,
                        exchangeUserId = currencyBalances.exchangeUserId,
                        exchangeUserName = currencyBalances.exchangeUserName,
                        exchange = exchangeBalance.exchangeName,
                        errorMessage = exchangeBalance.errorMessage,
                        insertTime = Timestamp(currentTimeMillis),
                    )
                )
                exchangeBalance.currencyBalances.forEach { currencyBalance ->
                    userExchangeWalletRepository.insertWallet(
                        UserExchangeWallet(
                            id = UUID.randomUUID().toString(),
                            userAccountId = userAccountId,
                            exchange = exchangeBalance.exchangeName,
                            exchangeUserId = currencyBalances.exchangeUserId,
                            currency = currencyBalance.currencyCode,
                            balance = currencyBalance.totalAmount.toBigDecimal(),
                            amountInOrders = currencyBalance.amountInOrders.toBigDecimal(),
                            amountAvailable = currencyBalance.amountAvailable.toBigDecimal(),
                        )
                    )
                }
            }
        }
    }

    fun getWalletBalances(userAccountId: String): ExchangeWalletBalancesDto {
        val userExchangeWallets = userExchangeWalletRepository().findManyByUserAccountId(userAccountId)
        val userExchangeWalletsLastRefresh = userExchangeWalletLastRefreshRepository().findManyByUserAccountId(userAccountId)
        val userExchangeWalletsLastRefreshGroupedByExchangeUserId = userExchangeWalletsLastRefresh.groupBy { it.exchangeUserId }
        val userExchangeWalletsGroupedByExchangeUser = userExchangeWallets.groupBy { it.exchangeUserId }
        val uniqueCurrenciesInWallets = userExchangeWallets.map { it.currency }.toSet()
        return ExchangeWalletBalancesDto(
            isShowingRealBalance = true,
            refreshTimeMillis = userExchangeWalletsLastRefresh.firstOrNull()?.insertTime?.time,
            pricesInOtherCurrencies = uniqueCurrenciesInWallets.associateWith { mapOf("USD" to priceService.getUsdPrice(it)?.price?.toPlainString()) },
            exchangeCurrencyBalances = userExchangeWalletsLastRefreshGroupedByExchangeUserId.map { userExchangeWalletsLastRefresh ->
                val userExchangeWallets = userExchangeWalletsGroupedByExchangeUser[userExchangeWalletsLastRefresh.key]
                val userExchangeWalletsGroupedByExchange = userExchangeWallets?.groupBy { it.exchange } ?: emptyMap()
                val exchangeUserId = userExchangeWalletsLastRefresh.key
                val exchangeUserName = userExchangeWalletsLastRefresh.value.first().exchangeUserName
                ExchangeCurrencyBalancesDto(
                    exchangeUserId = exchangeUserId,
                    exchangeUserName = exchangeUserName,
                    exchangeBalances = userExchangeWalletsLastRefresh.value.map { userExchangeWalletLastRefresh ->
                        ExchangeBalanceDto(
                            exchangeName = userExchangeWalletLastRefresh.exchange,
                            errorMessage = userExchangeWalletLastRefresh.errorMessage,
                            currencyBalances = userExchangeWalletsGroupedByExchange[userExchangeWalletLastRefresh.exchange]?.map {
                                ExchangeCurrencyBalanceDto(
                                    currencyCode = it.currency,
                                    amountAvailable = it.amountAvailable.toPlainString(),
                                    totalAmount = it.balance.toPlainString(),
                                    amountInOrders = it.amountInOrders.toPlainString(),
                                    valueInOtherCurrency = mapOf("USD" to priceService.getUsdValue(it.currency, it.balance)?.toPlainString()),
                                )
                            } ?: emptyList()
                        )
                    }
                )
            }
        )
    }

}
