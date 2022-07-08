package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.isUserInProPlan
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.wallet.currency.UserCurrencyAssetWithValue
import autocoin.balance.wallet.summary.BlockchainWalletCurrencySummary
import autocoin.balance.wallet.summary.CurrencyBalanceSummary
import autocoin.balance.wallet.summary.ExchangeCurrencySummary
import autocoin.balance.wallet.summary.UserBalanceSummaryService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods
import mu.KLogging

data class ExchangeCurrencySummaryDto(
    val exchangeName: String,
    val balance: String,
    val valueInOtherCurrency: Map<String, String?>?,
)

data class BlockchainWalletCurrencySummaryDto(
    val walletAddress: String,
    val balance: String?,
    val valueInOtherCurrency: Map<String, String?>?,
)

data class CurrencyAssetSummaryDto(
    val balance: String,
    val description: String?,
    val valueInOtherCurrency: Map<String, String?>,
)

fun ExchangeCurrencySummary.toDto() = ExchangeCurrencySummaryDto(
    exchangeName = this.exchangeName,
    balance = this.balance.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency?.toStringValueMap(),
)

fun BlockchainWalletCurrencySummary.toDto() = BlockchainWalletCurrencySummaryDto(
    walletAddress = this.walletAddress,
    balance = this.balance?.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency.toStringValueMap(),
)

fun UserCurrencyAssetWithValue.toSummaryDto() = CurrencyAssetSummaryDto(
    balance = this.userCurrencyAsset.balance.toPlainString(),
    description = this.userCurrencyAsset.description,
    valueInOtherCurrency = this.valueInOtherCurrency.toStringValueMap(),
)

data class CurrencyBalanceSummaryDto(
    val currency: String,
    val balance: String?,
    val valueInOtherCurrency: Map<String, String?>?,
    val priceInOtherCurrency: Map<String, String?>?,
    val exchanges: List<ExchangeCurrencySummaryDto>,
    val wallets: List<BlockchainWalletCurrencySummaryDto>,
    val currencyAssets: List<CurrencyAssetSummaryDto>,
)

fun CurrencyBalanceSummary.toDto() = CurrencyBalanceSummaryDto(
    currency = this.currency,
    balance = this.balance?.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency?.toStringValueMap(),
    priceInOtherCurrency = this.priceInOtherCurrency?.toStringValueMap(),
    exchanges = this.exchanges.map { it.toDto() },
    wallets = this.wallets.map { it.toDto() },
    currencyAssets = this.currencyAssets.map { it.toSummaryDto() },
)

data class BalanceSummaryResponseDto(
    val isShowingRealBalance: Boolean,
    val currencyBalances: List<CurrencyBalanceSummaryDto>,
)

class BalanceSummaryController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userBalanceSummaryService: UserBalanceSummaryService,
    private val isUserInProPlanFunction: (httpServerExchange: HttpServerExchange) -> Boolean = { it -> it.isUserInProPlan() },
    private val freePlanBalanceSummaryResponseDto: BalanceSummaryResponseDto = objectMapper.readValue(
        this::class.java.getResource("/freePlanBalanceSummaryResponse.json").readText(), BalanceSummaryResponseDto::class.java
    ),
) : ApiController {
    private companion object : KLogging()

    private val freePlanBalanceSummaryResponseJson: String by lazy {
        objectMapper.writeValueAsString(freePlanBalanceSummaryResponseDto)
    }

    private fun getUserBalanceSummary() = object : ApiEndpoint {
        override val method = Methods.GET
        override val urlTemplate = "/balance/summary"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val isProPlan = isUserInProPlanFunction(httpServerExchange)
            logger.info { "User $userAccountId is requesting balance summary (isProPlan=$isProPlan" }
            if (isProPlan) {
                val currencyBalanceSummaryList = userBalanceSummaryService.getCurrencyBalanceSummary(userAccountId)
                httpServerExchange.sendCurrencyBalanceSummary(currencyBalanceSummaryList)
            } else {
                httpServerExchange.responseSender.send(freePlanBalanceSummaryResponseJson)
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun HttpServerExchange.sendCurrencyBalanceSummary(currencyBalanceSummaryList: List<CurrencyBalanceSummary>) {
        this.responseSender.send(
            objectMapper.writeValueAsString(
                BalanceSummaryResponseDto(
                    isShowingRealBalance = true,
                    currencyBalances = currencyBalanceSummaryList.map { it.toDto() }
                )
            )
        )
    }

    private fun refreshUserBalanceSummary() = object : ApiEndpoint {
        override val method = Methods.POST
        override val urlTemplate = "/balance/summary"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val isProPlan = isUserInProPlanFunction(httpServerExchange)
            logger.info { "User $userAccountId is requesting balance refresh (isProPlan=$isProPlan)" }
            if (isProPlan) {
                userBalanceSummaryService.refreshBalanceSummary(userAccountId)
                logger.info { "User $userAccountId refreshed balance" }
                val currencyBalanceSummaryList = userBalanceSummaryService.getCurrencyBalanceSummary(userAccountId)
                httpServerExchange.sendCurrencyBalanceSummary(currencyBalanceSummaryList)
            } else {
                httpServerExchange.responseSender.send(freePlanBalanceSummaryResponseJson)
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getUserBalanceSummary(),
        refreshUserBalanceSummary(),
    )

}
