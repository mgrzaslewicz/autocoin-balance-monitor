package autocoin.balance.wallet.currency

import autocoin.balance.price.PriceService
import java.math.BigDecimal

data class UserCurrencyAssetWithValue(
    val userCurrencyAsset: UserCurrencyAsset,
    val valueInOtherCurrency: Map<String, BigDecimal?>
)

data class UserCurrencyAssetSummaryWithPriceAndValue(
    val userCurrencyAssetSummary: UserCurrencyAssetSummary,
    val valueInOtherCurrency: Map<String, BigDecimal?>,
    val priceInOtherCurrency: Map<String, BigDecimal?>,
)

class UserCurrencyAssetService(
    private val currencyAssetRepository: () -> UserCurrencyAssetRepository,
    private val priceService: PriceService,
) {
    fun getUserCurrencyAssets(userAccountId: String): List<UserCurrencyAssetWithValue> {
        return currencyAssetRepository().findManyByUserAccountId(userAccountId).map { it.withUsdPriceAndValue() }
    }

    private fun UserCurrencyAsset.withUsdPriceAndValue() = UserCurrencyAssetWithValue(
        userCurrencyAsset = this,
        valueInOtherCurrency = mapOf("USD" to priceService.getUsdValue(this.currency, this.balance)),

        )

    private fun UserCurrencyAssetSummary.withUsdPriceAndValue() = UserCurrencyAssetSummaryWithPriceAndValue(
        userCurrencyAssetSummary = this,
        valueInOtherCurrency = mapOf("USD" to priceService.getUsdValue(this.currency, this.balance)),
        priceInOtherCurrency = mapOf("USD" to priceService.getUsdPrice(this.currency)),
    )

    fun getUserCurrencyAsset(userAccountId: String, userCurrencyAssetId: String): UserCurrencyAssetWithValue? {
        val result = currencyAssetRepository().findOneByUserAccountIdAndId(userAccountId, userCurrencyAssetId)
        return result?.withUsdPriceAndValue()
    }

    fun getUserCurrencyAssetsSummary(userAccountId: String): List<UserCurrencyAssetSummaryWithPriceAndValue> {
        return currencyAssetRepository().selectUserCurrencyAssetSummary(userAccountId).map { it.withUsdPriceAndValue() }
    }

}
