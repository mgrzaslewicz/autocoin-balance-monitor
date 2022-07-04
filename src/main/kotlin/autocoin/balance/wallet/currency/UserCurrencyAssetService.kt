package autocoin.balance.wallet.currency

import autocoin.balance.price.PriceService
import java.math.BigDecimal

data class UserCurrencyAssetWithValue(
    val userCurrencyAsset: UserCurrencyAsset,
    val valueInOtherCurrency: Map<String, BigDecimal?>
)

data class UserCurrencyAssetSummaryWithValue(
    val userCurrencyAssetSummary: UserCurrencyAssetSummary,
    val valueInOtherCurrency: Map<String, BigDecimal?>
)

class UserCurrencyAssetService(
    private val currencyAssetRepository: () -> UserCurrencyAssetRepository,
    private val priceService: PriceService,
) {
    fun getUserCurrencyAssets(userAccountId: String): List<UserCurrencyAssetWithValue> {
        return currencyAssetRepository().findManyByUserAccountId(userAccountId).map { it.withUsdValue() }
    }

    private fun UserCurrencyAsset.withUsdValue() = UserCurrencyAssetWithValue(
        userCurrencyAsset = this,
        valueInOtherCurrency = mapOf("USD" to priceService.getUsdValue(this.currency, this.balance))
    )

    private fun UserCurrencyAssetSummary.withUsdValue() = UserCurrencyAssetSummaryWithValue(
        userCurrencyAssetSummary = this,
        valueInOtherCurrency = mapOf("USD" to priceService.getUsdValue(this.currency, this.balance))
    )

    fun getUserCurrencyAsset(userAccountId: String, userCurrencyAssetId: String): UserCurrencyAssetWithValue? {
        val result = currencyAssetRepository().findOneByUserAccountIdAndId(userAccountId, userCurrencyAssetId)
        return result?.withUsdValue()
    }

    fun getUserCurrencyAssetsSummary(userAccountId: String): List<UserCurrencyAssetSummaryWithValue> {
        return currencyAssetRepository().selectUserCurrencyAssetSummary(userAccountId).map { it.withUsdValue() }
    }

}
