package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.blockchain.MultiWalletAddressValidator
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserBlockChainWallet
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.blockchain.UserBlockChainWalletService
import autocoin.balance.wallet.blockchain.UserCurrencyBalance
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods.*
import io.undertow.util.PathTemplateMatch
import mu.KLogging
import java.math.BigDecimal

data class CreateWalletRequestDto(
    val walletAddress: String,
    val currency: String,
    val description: String?,
)

data class UpdateWalletRequestDto(
    val id: String,
    val walletAddress: String,
    val currency: String,
    val description: String?,
)

data class UpdateWalletErrorResponseDto(
    val isAddressDuplicated: Boolean = false,
    val isAddressInvalid: Boolean = false,
    val isIdInvalid: Boolean = false,
)

data class CreateWalletsErrorResponseDto(
    val duplicatedAddresses: List<String>,
    val invalidAddresses: List<String>,
)

data class WalletResponseDto(
    val id: String,
    val walletAddress: String,
    val currency: String,
    val description: String?,
    val balance: String?,
    val usdBalance: String?,
)

fun UserBlockChainWallet.toDto(usdBalance: BigDecimal?) = WalletResponseDto(
    id = this.id,
    walletAddress = this.walletAddress,
    currency = this.currency,
    description = this.description,
    balance = this.balance?.stripTrailingZeros()?.toPlainString(),
    usdBalance = usdBalance?.stripTrailingZeros()?.toPlainString(),
)

fun CreateWalletRequestDto.toUserBlockChainWallet(userAccountId: String) = UserBlockChainWallet(
    walletAddress = this.walletAddress,
    currency = this.currency,
    userAccountId = userAccountId,
    description = this.description,
    balance = null,
)

data class UserCurrencyBalanceResponseDto(
    val currency: String,
    val balance: String,
    val usdBalance: String?, // in case of with getting usd price, better send anything
)

fun UserCurrencyBalance.toDto(usdBalance: BigDecimal?) = UserCurrencyBalanceResponseDto(
    currency = this.currency,
    balance = this.balance.stripTrailingZeros().toPlainString(),
    usdBalance = usdBalance?.stripTrailingZeros()?.toPlainString(),
)

class WalletController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository,
    private val walletAddressValidator: MultiWalletAddressValidator,
    private val userBlockChainWalletService: UserBlockChainWalletService,
    private val priceService: PriceService,
) : ApiController {

    private companion object : KLogging()


    private fun addMonitoredWallets() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val addWalletsRequest = httpServerExchange.inputStreamToObject(Array<CreateWalletRequestDto>::class.java).toList()
            logger.info { "User $userAccountId is adding wallets: $addWalletsRequest" }
            val duplicatedWalletAddresses = mutableListOf<String>()
            val invalidAddresses = mutableListOf<String>()
            addWalletsRequest.forEach {
                val walletToAdd = it.toUserBlockChainWallet(userAccountId)
                if (!walletAddressValidator.isWalletAddressValid(walletToAdd.currency, walletToAdd.walletAddress)) {
                    invalidAddresses += walletToAdd.walletAddress
                } else {
                    try {
                        val walletAddResult = userBlockChainWalletService.addWallet(userAccountId, walletToAdd)
                        if (walletAddResult.userAlreadyHasWalletWithThisAddress) {
                            duplicatedWalletAddresses += walletToAdd.walletAddress
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Could not add wallet $walletToAdd" }
                        throw e
                    }
                }
            }
            if (duplicatedWalletAddresses.isNotEmpty() || invalidAddresses.isNotEmpty()) {
                httpServerExchange.statusCode = 400
                httpServerExchange.sendJson(
                    CreateWalletsErrorResponseDto(
                        duplicatedAddresses = duplicatedWalletAddresses,
                        invalidAddresses = invalidAddresses,
                    )
                )
            }
            logger.info { "User $userAccountId added wallets: $addWalletsRequest" }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun <T> HttpServerExchange.inputStreamToObject(clazz: Class<T>): T {
        this.startBlocking() // in order to user inputStream
        return objectMapper.readValue(this.inputStream, clazz)
    }

    private fun updateWallet() = object : ApiEndpoint {
        override val method = PUT
        override val urlTemplate = "/wallet"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val walletUpdateRequest = httpServerExchange.inputStreamToObject(UpdateWalletRequestDto::class.java)
            logger.info { "User $userAccountId is updating wallet: $walletUpdateRequest" }
            if (!walletAddressValidator.isWalletAddressValid(walletUpdateRequest.currency, walletUpdateRequest.walletAddress)) {
                httpServerExchange.statusCode = 400
                httpServerExchange.sendJson(UpdateWalletErrorResponseDto(isAddressInvalid = true))
            } else try {
                val updateResult = userBlockChainWalletService.updateWallet(userAccountId, walletUpdateRequest)
                if (updateResult.isSuccessful()) {
                    logger.info { "User $userAccountId updated wallet: $walletUpdateRequest" }
                } else {
                    logger.warn { "User $userAccountId dit not update wallet $walletUpdateRequest successfully: $updateResult" }
                    httpServerExchange.statusCode = 400
                    httpServerExchange.sendJson(
                        UpdateWalletErrorResponseDto(
                            isAddressDuplicated = updateResult.userAlreadyHasWalletWithThisAddress,
                            isIdInvalid = updateResult.userHasNoWalletWithGivenId,
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Could not update wallet $walletUpdateRequest" }
                throw e
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun HttpServerExchange.sendUserWallets(userAccountId: String) {
        val wallets = userBlockChainWalletRepository().findManyByUserAccountId(userAccountId)
            .map {
                val usdBalance = tryGetUsdValue(it.currency, it.balance)
                it.toDto(usdBalance)
            }
        this.sendJson(wallets)
    }

    private fun HttpServerExchange.sendUserWallet(walletId: String) {
        val wallet = userBlockChainWalletRepository().findOneById(walletId)
        val usdBalance = if (wallet.balance == null) null else priceService.getUsdValueOrNull(wallet.currency, wallet.balance)
        this.sendJson(wallet.toDto(usdBalance))
    }

    private fun <T> HttpServerExchange.sendJson(response: T) {
        this.responseSender.send(objectMapper.writeValueAsString(response))
    }

    private fun getMonitoredWallets() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting wallets" }
            httpServerExchange.sendUserWallets(userAccountId)
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun getMonitoredWallet() = object : ApiEndpoint {
        private val walletIdParameter = "walletId"
        override val method = GET
        override val urlTemplate = "/wallets/{$walletIdParameter}"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val pathMatch: PathTemplateMatch = httpServerExchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
            val walletId = pathMatch.parameters[walletIdParameter]
            logger.info { "User $userAccountId is requesting wallet $walletId" }
            if (walletId != null) {
                if (userBlockChainWalletRepository().existsByUserAccountIdAndId(userAccountId, walletId)) {
                    httpServerExchange.sendUserWallet(walletId)
                } else {
                    httpServerExchange.statusCode = 404
                }
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun tryGetUsdValue(currency: String, currencyBalance: BigDecimal?): BigDecimal? {
        return if (currencyBalance != null) {
            priceService.getUsdValueOrNull(currency, currencyBalance)
        } else {
            null
        }
    }

    private fun getCurrencyBalance() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/wallets/currency/balance"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is requesting wallets currency balance" }
            val currencyBalance = userBlockChainWalletRepository().selectUserCurrencyBalance(userAccountId)
            httpServerExchange.sendJson(currencyBalance.map {
                val usdBalance = tryGetUsdValue(it.currency, it.balance)
                it.toDto(usdBalance)
            })
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun refreshWalletsBalance() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/wallets/balance/refresh"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is refreshing wallets balance" }
            userBlockChainWalletService.refreshWalletBalances(userAccountId)
            logger.info { "User $userAccountId refreshed wallets balance" }
            httpServerExchange.sendUserWallets(userAccountId)
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun deleteWallet() = object : ApiEndpoint {
        private val walletAddressParameter = "address"

        override val method = DELETE
        override val urlTemplate = "/wallet/{$walletAddressParameter}"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val pathMatch: PathTemplateMatch = httpServerExchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
            val walletAddress = pathMatch.parameters[walletAddressParameter]
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is deleting wallet $walletAddress" }
            if (walletAddress != null) {
                val howManyDeleted = userBlockChainWalletRepository().deleteOneByUserAccountIdAndWalletAddress(userAccountId, walletAddress)
                if (howManyDeleted == 0) {
                    logger.warn { "User $userAccountId tried to delete wallet $walletAddress which was not found. That might be a hack attempt or just missing wallet" }
                    httpServerExchange.statusCode = 404
                }
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        addMonitoredWallets(),
        getMonitoredWallets(),
        getMonitoredWallet(),
        getCurrencyBalance(),
        refreshWalletsBalance(),
        deleteWallet(),
        updateWallet(),
    )
}
