package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.isUserInProPlan
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserCurrencyBalance
import autocoin.balance.wallet.exchange.ExchangeWalletBalancesDto
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWalletService
import automate.profit.autocoin.exchange.time.TimeMillisProvider
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods.GET
import io.undertow.util.Methods.POST
import mu.KLogging
import java.math.BigDecimal

class ExchangeWalletController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userExchangeWalletService: UserExchangeWalletService,
    private val userExchangeWalletRepository: () -> UserExchangeWalletRepository,
    private val priceService: PriceService,
    private val isUserInProPlanFunction: (httpServerExchange: HttpServerExchange) -> Boolean = { it -> it.isUserInProPlan() },
    private val freePlanExchangeWalletBalancesResponseDto: ExchangeWalletBalancesDto = objectMapper.readValue(
        this::class.java.getResource("/freePlanExchangeWalletBalancesResponse.json").readText(), ExchangeWalletBalancesDto::class.java
    ),
    private val timeMillisProvider: TimeMillisProvider,
) : ApiController {

    private companion object : KLogging()

    private fun <T> HttpServerExchange.sendJson(response: T) {
        this.responseSender.send(objectMapper.writeValueAsString(response))
    }

    private fun getExchangeWallets() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/exchange/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val isProPlan = isUserInProPlanFunction(httpServerExchange)
            logger.info { "User $userAccountId is requesting exchange wallets (isProPlan=$isProPlan" }
            if (isProPlan) {
                httpServerExchange.sendJson(userExchangeWalletService.getWalletBalances(userAccountId))
            } else {
                httpServerExchange.sendJson(freePlanExchangeWalletBalancesResponseDto)
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun tryGetUsdValue(currency: String, currencyBalance: BigDecimal?): BigDecimal? {
        return if (currencyBalance != null) {
            priceService.getUsdValue(currency, currencyBalance)
        } else {
            null
        }
    }

    private fun getCurrencyBalance() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/exchange/wallets/currency/balance"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val isProPlan = isUserInProPlanFunction(httpServerExchange)
            logger.info { "User $userAccountId is requesting exchange wallets currency balance (isProPlan=$isProPlan" }
            if (isProPlan) {
                val currencyBalance = userExchangeWalletRepository().selectUserCurrencyBalance(userAccountId)
                httpServerExchange.sendJson(currencyBalance.map {
                    val usdBalance = tryGetUsdValue(it.currency, it.balance)
                    val usdPrice = priceService.getUsdPrice(it.currency)?.price
                    it.toDto(usdBalance, usdPrice)
                })
            } else {
                httpServerExchange.sendJson(emptyList<UserCurrencyBalance>())
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun refreshExchangeWalletsBalance() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/exchange/wallets/balance/refresh"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val isProPlan = isUserInProPlanFunction(httpServerExchange)
            logger.info { "User $userAccountId is refreshing exchange wallets balance (isProPlan=$isProPlan" }
            if (isProPlan) {
                userExchangeWalletService.refreshWalletBalances(userAccountId)
                logger.info { "User $userAccountId refreshed exchange wallets balance" }
                httpServerExchange.sendJson(userExchangeWalletService.getWalletBalances(userAccountId))
            } else {
                httpServerExchange.sendJson(freePlanExchangeWalletBalancesResponseDto.copy(refreshTimeMillis = timeMillisProvider.now()))
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getExchangeWallets(),
        getCurrencyBalance(),
        refreshExchangeWalletsBalance(),
    )
}
