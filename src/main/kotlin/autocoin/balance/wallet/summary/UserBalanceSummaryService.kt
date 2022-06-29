package autocoin.balance.wallet.summary

import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import java.math.BigDecimal

data class ExchangeCurrencySummary(
    val exchangeName: String,
    val balance: BigDecimal,
    val valueInOtherCurrency: Map<String, BigDecimal?>?,
)

data class BlockchainWalletCurrencySummary(
    val walletAddress: String,
    val balance: BigDecimal?,
    val valueInOtherCurrency: Map<String, BigDecimal?>,
)

data class CurrencyBalanceSummary(
    val currency: String,
    val balance: BigDecimal?,
    val valueInOtherCurrency: Map<String, BigDecimal?>?,
    val exchanges: List<ExchangeCurrencySummary>,
    val wallets: List<BlockchainWalletCurrencySummary>,
)


class UserBalanceSummaryService(
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository,
    private val userExchangeWalletRepository: () -> UserExchangeWalletRepository,
    private val userBalanceSummaryRepository: () -> UserBalanceSummaryRepository,
    private val priceService: PriceService,
) {
    fun getCurrencyBalanceSummary(userAccountId: String): List<CurrencyBalanceSummary> {
        return userBalanceSummaryRepository().findUniqueUserCurrencies(userAccountId).map { currency ->

            val userBlockChainWallets = userBlockChainWalletRepository().findManyByUserAccountIdAndCurrency(userAccountId, currency = currency)
            val userExchangeWallets = userExchangeWalletRepository().findManyByUserAccountIdAndCurrency(userAccountId, currency = currency)

            val sumOfBalances = if (userBlockChainWallets.all { it.balance == null }) {
                null
            } else {
                userBlockChainWallets.fold(BigDecimal.ZERO) { acc, it ->
                    acc.plus(it.balance ?: BigDecimal.ZERO)
                }.plus(userExchangeWallets.fold(BigDecimal.ZERO) { acc, it ->
                    acc.plus(it.balance)
                })
            }
            CurrencyBalanceSummary(
                currency = currency,
                balance = sumOfBalances,
                valueInOtherCurrency = mapOf("USD" to if (sumOfBalances == null) null else priceService.getUsdValueOrNull(currency, sumOfBalances)),
                exchanges = userExchangeWallets.map {
                    ExchangeCurrencySummary(
                        exchangeName = it.exchange,
                        balance = it.balance,
                        valueInOtherCurrency = mapOf("USD" to priceService.getUsdValueOrNull(currency, it.balance))
                    )
                },
                wallets = userBlockChainWallets.map {
                    BlockchainWalletCurrencySummary(
                        walletAddress = it.walletAddress,
                        balance = it.balance,
                        valueInOtherCurrency = mapOf("USD" to if (it.balance == null) null else priceService.getUsdValueOrNull(currency, it.balance))
                    )
                },
            )
        }
    }
}
