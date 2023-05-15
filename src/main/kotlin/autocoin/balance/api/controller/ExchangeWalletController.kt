package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.oauth.server.authorizeWithOauth2
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
    private val shouldSendSampleBalance: (httpServerExchange: HttpServerExchange) -> Boolean = { it ->
        it.queryParameters["sampleBalance"]?.first == "true"
    },
    private val sampleExchangeWalletBalancesResponseDto: ExchangeWalletBalancesDto = objectMapper.readValue(
        this::class.java.getResource("/sampleExchangeWalletBalancesResponse.json").readText(), ExchangeWalletBalancesDto::class.java
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
            val shouldSendSampleBalance = shouldSendSampleBalance(httpServerExchange)
            logger.info { "User $userAccountId is requesting exchange wallets (shouldSendSampleBalance=$shouldSendSampleBalance)" }
            if (shouldSendSampleBalance) {
                httpServerExchange.sendJson(sampleExchangeWalletBalancesResponseDto)
            } else {
                httpServerExchange.sendJson(userExchangeWalletService.getWalletBalances(userAccountId))
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
            val shouldSendSampleBalance = shouldSendSampleBalance(httpServerExchange)
            logger.info { "User $userAccountId is requesting exchange wallets currency balance (shouldSendSampleBalance=$shouldSendSampleBalance)" }
            if (shouldSendSampleBalance) {
                httpServerExchange.sendJson(emptyList<UserCurrencyBalance>())
            } else {
                val currencyBalance = userExchangeWalletRepository().selectUserCurrencyBalance(userAccountId)
                httpServerExchange.sendJson(currencyBalance.map {
                    val usdBalance = tryGetUsdValue(it.currency, it.balance)
                    val usdPrice = priceService.getUsdPrice(it.currency)?.price
                    it.toDto(usdBalance, usdPrice)
                })
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun refreshExchangeWalletsBalance() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/exchange/wallets/balance/refresh"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val shouldSendSampleBalance = shouldSendSampleBalance(httpServerExchange)
            logger.info { "User $userAccountId is refreshing exchange wallets balance (shouldSendSampleBalance=$shouldSendSampleBalance)" }
            if (shouldSendSampleBalance) {
                httpServerExchange.sendJson(sampleExchangeWalletBalancesResponseDto.copy(refreshTimeMillis = timeMillisProvider.now()))
            } else {
                userExchangeWalletService.refreshWalletBalances(userAccountId)
                logger.info { "User $userAccountId refreshed exchange wallets balance" }
                httpServerExchange.sendJson(userExchangeWalletService.getWalletBalances(userAccountId))
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getExchangeWallets(),
        getCurrencyBalance(),
        refreshExchangeWalletsBalance(),
    )
}
