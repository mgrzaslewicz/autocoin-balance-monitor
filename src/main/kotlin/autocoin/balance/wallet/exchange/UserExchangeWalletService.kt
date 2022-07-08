package autocoin.balance.wallet.exchange

import autocoin.balance.price.PriceService
import automate.profit.autocoin.exchange.time.SystemTimeMillisProvider
import automate.profit.autocoin.exchange.time.TimeMillisProvider
import automate.profit.autocoin.exchange.wallet.ExchangeBalanceDto
import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalanceDto
import automate.profit.autocoin.exchange.wallet.ExchangeCurrencyBalancesDto
import mu.KLogging
import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*


data class ExchangeWalletBalancesDto(
    val refreshTimeMillis: Long?,
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
        exchangeUserBalances.forEach { currencyBalances ->
            currencyBalances.exchangeBalances.forEach { exchangeBalance ->
                userExchangeWalletLastRefreshRepository.insertWalletLastRefresh(
                    UserExchangeWalletLastRefresh(
                        userAccountId = userAccountId,
                        exchangeUserId = currencyBalances.exchangeUserId,
                        exchange = exchangeBalance.exchangeName,
                        errorMessage = exchangeBalance.errorMessage,
                        insertTime = Timestamp(timeMillisProvider.now()),
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

    private fun getUsdValueOrNull(currency: String, amount: BigDecimal): BigDecimal? {
        val usdPrice = priceService.getUsdValueOrNull(currency, amount)
        return usdPrice
    }

    fun getWalletBalances(userAccountId: String): ExchangeWalletBalancesDto {
        val userExchangeWallets = userExchangeWalletRepository().findManyByUserAccountId(userAccountId)
        val userExchangeWalletsLastRefresh = userExchangeWalletLastRefreshRepository().findManyByUserAccountId(userAccountId)
        val userExchangeWalletsLastRefreshGroupedByExchangeUser = userExchangeWalletsLastRefresh.groupBy { it.exchangeUserId }
        val userExchangeWalletsGroupedByExchangeUser = userExchangeWallets.groupBy { it.exchangeUserId }
        return ExchangeWalletBalancesDto(
            refreshTimeMillis = userExchangeWalletsLastRefresh.firstOrNull()?.insertTime?.time,
            exchangeCurrencyBalances = userExchangeWalletsLastRefreshGroupedByExchangeUser.map { userExchangeWalletsLastRefresh ->
                val userExchangeWallets = userExchangeWalletsGroupedByExchangeUser[userExchangeWalletsLastRefresh.key]
                val userExchangeWalletsGroupedByExchange = userExchangeWallets?.groupBy { it.exchange } ?: emptyMap()
                val userExchangeId = userExchangeWalletsLastRefresh.key
                ExchangeCurrencyBalancesDto(
                    exchangeUserId = userExchangeId,
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
                                    valueInOtherCurrency = mapOf("USD" to getUsdValueOrNull(it.currency, it.balance)?.toPlainString())
                                )
                            } ?: emptyList()
                        )
                    }
                )
            }
        )
    }

}
