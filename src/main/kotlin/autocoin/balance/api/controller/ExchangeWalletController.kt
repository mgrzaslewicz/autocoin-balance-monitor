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
    private val timeMillisProvider: TimeMillisProvider,
    private val sampleExchangeWalletBalancesResponseDtoJson: ExchangeWalletBalancesDto = objectMapper.readValue(
        this::class.java.getResource("/sampleExchangeWalletBalancesResponse.json").readText(),
        ExchangeWalletBalancesDto::class.java
    ),
) : ApiController {

    private companion object : KLogging()

    private fun Map<String, Map<String, String?>>.updatePrices(): Map<String, Map<String, String?>> {
        return this.map { baseWithPrices ->
            baseWithPrices.key to baseWithPrices.value.map {
                it.key to priceService.getPrice(baseWithPrices.key, it.key)?.price?.toPlainString()
            }.toMap()
        }.toMap()
    }

    private fun Map<String, String?>?.updateValueInOtherCurrency(
        baseCurrencyCode: String,
        amount: String,
    ): Map<String, String?>? {
        return this?.map { currencyWithValueInOtherCurrency ->
            currencyWithValueInOtherCurrency.key to priceService.getValue(
                baseCurrency = baseCurrencyCode,
                counterCurrency = currencyWithValueInOtherCurrency.key,
                baseCurrencyAmount = amount.toBigDecimal(),
            )?.toPlainString()
        }?.toMap()
    }

    private val sampleExchangeWalletBalancesResponseDto by lazy {
        sampleExchangeWalletBalancesResponseDtoJson.copy(
                    refreshTimeMillis = timeMillisProvider.now(),
                    pricesInOtherCurrencies = sampleExchangeWalletBalancesResponseDtoJson.pricesInOtherCurrencies.updatePrices(),
                    exchangeCurrencyBalances = sampleExchangeWalletBalancesResponseDtoJson.exchangeCurrencyBalances.map {
                        it.copy(
                            exchangeBalances = it.exchangeBalances.map { exchangeBalance ->
                                exchangeBalance.copy(
                                    currencyBalances = exchangeBalance.currencyBalances.map { currencyBalance ->
                                        currencyBalance.copy(
                                            valueInOtherCurrency = currencyBalance.valueInOtherCurrency.updateValueInOtherCurrency(
                                                baseCurrencyCode = currencyBalance.currencyCode,
                                                amount = currencyBalance.totalAmount,
                                            ),
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
    }

    private fun <T> HttpServerExchange.sendJson(response: T) {
        this.responseSender.send(objectMapper.writeValueAsString(response))
    }

    private fun getSampleExchangeWallets() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/exchange/wallets/sample"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting sample exchange wallets" }
            httpServerExchange.sendJson(sampleExchangeWalletBalancesResponseDto)
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun getExchangeWallets() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/exchange/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting exchange wallets" }
            httpServerExchange.sendJson(userExchangeWalletService.getWalletBalances(userAccountId))
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun tryGetUsdValue(currency: String, currencyBalance: BigDecimal?): BigDecimal? {
        return if (currencyBalance != null) {
            priceService.getUsdValue(currency, currencyBalance)
        } else {
            null
        }
    }

    private fun getSampleCurrencyBalance() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/exchange/wallets/currency/balance/sample"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting sample exchange wallets currency balance" }
            httpServerExchange.sendJson(emptyList<UserCurrencyBalance>())
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun getCurrencyBalance() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/exchange/wallets/currency/balance"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting exchange wallets currency balance" }
            val currencyBalance = userExchangeWalletRepository().selectUserCurrencyBalance(userAccountId)
            httpServerExchange.sendJson(currencyBalance.map {
                val usdBalance = tryGetUsdValue(it.currency, it.balance)
                val usdPrice = priceService.getUsdPrice(it.currency)?.price
                it.toDto(usdBalance, usdPrice)
            })
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun refreshExchangeWalletsBalance() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/exchange/wallets/balance/refresh"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is refreshing exchange wallets balance" }
            userExchangeWalletService.refreshWalletBalances(userAccountId)
            logger.info { "User $userAccountId refreshed exchange wallets balance" }
            httpServerExchange.sendJson(userExchangeWalletService.getWalletBalances(userAccountId))
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getExchangeWallets(),
        getSampleExchangeWallets(),
        getCurrencyBalance(),
        getSampleCurrencyBalance(),
        refreshExchangeWalletsBalance(),
    )
}
