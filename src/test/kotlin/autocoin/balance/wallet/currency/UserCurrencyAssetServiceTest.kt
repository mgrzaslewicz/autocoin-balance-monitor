package autocoin.balance.wallet.currency

import autocoin.balance.price.PriceService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
class UserCurrencyAssetServiceTest {

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var userCurrencyAssetRepository: UserCurrencyAssetRepository


    @Test
    fun shouldGetCurrencyAssets() {
        // given
        val userAccountId = "user-account-id-1"
        val currencyAsset1 = UserCurrencyAsset(
            userAccountId = userAccountId,
            currency = "ETH",
            balance = BigDecimal("0.5"),
            description = null
        )
        val currencyAsset2 = UserCurrencyAsset(
            userAccountId = userAccountId,
            currency = "BTC",
            balance = BigDecimal("2.5"),
            description = "binance"
        )
        val tested = UserCurrencyAssetService(
            priceService = priceService.apply {
                whenever(this.getUsdValue("ETH", BigDecimal("0.5"))).thenReturn(BigDecimal("10000"))
            },
            currencyAssetRepository = {
                userCurrencyAssetRepository.apply {
                    whenever(this.findManyByUserAccountId(userAccountId)).thenReturn(listOf(currencyAsset1, currencyAsset2))
                }
            }
        )
        // when
        val userCurrencyAssets = tested.getUserCurrencyAssets(userAccountId)
        // then
        assertThat(userCurrencyAssets).containsExactlyInAnyOrder(
            UserCurrencyAssetWithValue(userCurrencyAsset = currencyAsset1, valueInOtherCurrency = mapOf("USD" to BigDecimal("10000"))),
            UserCurrencyAssetWithValue(userCurrencyAsset = currencyAsset2, valueInOtherCurrency = mapOf("USD" to null))
        )
    }

    @Test
    fun shouldGetCurrencyAssetsSummary() {
        // given
        val userAccountId = "user-account-id-1"
        val currencyAssetSummary1 = UserCurrencyAssetSummary(
            currency = "BTC",
            balance = BigDecimal("10.5"),
        )
        val currencyAssetSummary2 = UserCurrencyAssetSummary(
            currency = "ETH",
            balance = BigDecimal("2.5"),
        )
        val tested = UserCurrencyAssetService(
            priceService = priceService.apply {
                whenever(this.getUsdValue("BTC", BigDecimal("10.5"))).thenReturn(BigDecimal("20000"))
                whenever(this.getUsdPrice("BTC")).thenReturn(BigDecimal("26000"))
            },
            currencyAssetRepository = {
                userCurrencyAssetRepository.apply {
                    whenever(this.selectUserCurrencyAssetSummary(userAccountId)).thenReturn(listOf(currencyAssetSummary1, currencyAssetSummary2))
                }
            }
        )
        // when
        val userCurrencyAssetsSummary = tested.getUserCurrencyAssetsSummary(userAccountId)
        // then
        assertThat(userCurrencyAssetsSummary).containsExactlyInAnyOrder(
            UserCurrencyAssetSummaryWithPriceAndValue(
                userCurrencyAssetSummary = currencyAssetSummary1,
                valueInOtherCurrency = mapOf("USD" to BigDecimal("20000")),
                priceInOtherCurrency = mapOf("USD" to BigDecimal("26000")),
            ),
            UserCurrencyAssetSummaryWithPriceAndValue(
                userCurrencyAssetSummary = currencyAssetSummary2,
                valueInOtherCurrency = mapOf("USD" to null),
                priceInOtherCurrency = mapOf("USD" to null),
            )
        )
    }

}
