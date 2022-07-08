package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.wallet.summary.BlockchainWalletCurrencySummary
import autocoin.balance.wallet.summary.CurrencyBalanceSummary
import autocoin.balance.wallet.summary.ExchangeCurrencySummary
import autocoin.balance.wallet.summary.UserBalanceSummaryService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
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

fun ExchangeCurrencySummary.toDto() = ExchangeCurrencySummaryDto(
    exchangeName = this.exchangeName,
    balance = this.balance.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency?.map { entry -> entry.key to entry.value?.toPlainString() }?.toMap(),
)

fun BlockchainWalletCurrencySummary.toDto() = BlockchainWalletCurrencySummaryDto(
    walletAddress = this.walletAddress,
    balance = this.balance?.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency.map { entry -> entry.key to entry.value?.toPlainString() }.toMap(),
)

data class CurrencyBalanceSummaryDto(
    val currency: String,
    val balance: String?,
    val valueInOtherCurrency: Map<String, String?>?,
    val exchanges: List<ExchangeCurrencySummaryDto>,
    val wallets: List<BlockchainWalletCurrencySummaryDto>,
)

fun CurrencyBalanceSummary.toDto() = CurrencyBalanceSummaryDto(
    currency = this.currency,
    balance = this.balance?.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency?.map { entry -> entry.key to entry.value?.toPlainString() }?.toMap(),
    exchanges = this.exchanges.map { it.toDto() },
    wallets = this.wallets.map { it.toDto() },
)

data class BalanceSummaryResponseDto(
    val currencyBalances: List<CurrencyBalanceSummaryDto>,
)

class BalanceSummaryController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userBalanceSummaryService: UserBalanceSummaryService,
) : ApiController {
    private companion object : KLogging()

    private fun getUserBalanceSummary() = object : ApiEndpoint {
        override val method = Methods.GET
        override val urlTemplate = "/balance/summary"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting balance summary" }
            val currencyBalanceSummaryList = userBalanceSummaryService.getCurrencyBalanceSummary(userAccountId)
            httpServerExchange.responseSender.send(
                objectMapper.writeValueAsString(
                    BalanceSummaryResponseDto(
                        currencyBalances = currencyBalanceSummaryList.map { it.toDto() }
                    )
                )
            )
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getUserBalanceSummary()
    )

}
