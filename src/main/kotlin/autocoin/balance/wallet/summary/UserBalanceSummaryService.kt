package autocoin.balance.wallet.summary

import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.blockchain.UserBlockChainWalletService
import autocoin.balance.wallet.currency.UserCurrencyAssetService
import autocoin.balance.wallet.currency.UserCurrencyAssetWithValue
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWalletService
import java.math.BigDecimal
import java.util.concurrent.ExecutorService

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
    val currencyAssets: List<UserCurrencyAssetWithValue>
)

interface UserBalanceSummaryService {
    fun getCurrencyBalanceSummary(userAccountId: String): List<CurrencyBalanceSummary>
    fun refreshBalanceSummary(userAccountId: String): List<CurrencyBalanceSummary>

}

class DefaultUserBalanceSummaryService(
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository,
    private val userExchangeWalletRepository: () -> UserExchangeWalletRepository,
    private val userBalanceSummaryRepository: () -> UserBalanceSummaryRepository,
    private val userCurrencyAssetService: UserCurrencyAssetService,
    private val priceService: PriceService,
    private val executorService: ExecutorService,
    private val userBlockChainWalletService: UserBlockChainWalletService,
    private val userExchangeWalletService: UserExchangeWalletService,
) : UserBalanceSummaryService {
    override fun getCurrencyBalanceSummary(userAccountId: String): List<CurrencyBalanceSummary> {
        val userCurrencyAssetsGroupedByCurrency = userCurrencyAssetService.getUserCurrencyAssets(userAccountId).groupBy { it.userCurrencyAsset.currency }
        return userBalanceSummaryRepository().findUniqueUserCurrencies(userAccountId).map { currency ->

            val userBlockChainWallets = userBlockChainWalletRepository().findManyByUserAccountIdAndCurrency(userAccountId, currency = currency)
            val userExchangeWallets = userExchangeWalletRepository().findManyByUserAccountIdAndCurrency(userAccountId, currency = currency)
            val userCurrencyAssetsForThisCurrency = userCurrencyAssetsGroupedByCurrency.getOrDefault(currency, emptyList())

            val sumOfBalances = if (userBlockChainWallets.all { it.balance == null } && userExchangeWallets.isEmpty() && userCurrencyAssetsForThisCurrency.isEmpty()) {
                null
            } else {
                userBlockChainWallets.fold(BigDecimal.ZERO) { acc, it ->
                    acc.plus(it.balance ?: BigDecimal.ZERO)
                }.plus(userExchangeWallets.fold(BigDecimal.ZERO) { acc, it ->
                    acc.plus(it.balance)
                }).plus(userCurrencyAssetsForThisCurrency.fold(BigDecimal.ZERO) { acc, it -> acc.plus(it.userCurrencyAsset.balance) })
            }
            CurrencyBalanceSummary(
                currency = currency,
                balance = sumOfBalances,
                valueInOtherCurrency = mapOf("USD" to if (sumOfBalances == null) null else priceService.getUsdValue(currency, sumOfBalances)),
                exchanges = userExchangeWallets.map {
                    ExchangeCurrencySummary(
                        exchangeName = it.exchange,
                        balance = it.balance,
                        valueInOtherCurrency = mapOf("USD" to priceService.getUsdValue(currency, it.balance))
                    )
                },
                wallets = userBlockChainWallets.map {
                    BlockchainWalletCurrencySummary(
                        walletAddress = it.walletAddress,
                        balance = it.balance,
                        valueInOtherCurrency = mapOf("USD" to if (it.balance == null) null else priceService.getUsdValue(currency, it.balance))
                    )
                },
                currencyAssets = userCurrencyAssetsForThisCurrency,
            )
        }
    }

    override fun refreshBalanceSummary(userAccountId: String): List<CurrencyBalanceSummary> {
        val refreshBlockChainWalletsFuture = executorService.submit {
            userBlockChainWalletService.refreshWalletBalances(userAccountId)
        }
        val refreshExchangeWalletsFuture = executorService.submit {
            userExchangeWalletService.refreshWalletBalances(userAccountId)
        }
        refreshBlockChainWalletsFuture.get()
        refreshExchangeWalletsFuture.get()
        return getCurrencyBalanceSummary(userAccountId)
    }

}
