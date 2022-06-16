package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.blockchain.eth.EthService
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.wallet.UserBlockChainWallet
import autocoin.balance.wallet.UserBlockChainWalletRepository
import autocoin.balance.wallet.UserBlockChainWalletService
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods.*
import io.undertow.util.PathTemplateMatch
import mu.KLogging

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
)

fun UserBlockChainWallet.toDto() = WalletResponseDto(
    id = this.id,
    walletAddress = this.walletAddress,
    currency = this.currency,
    description = this.description,
    balance = this.balance?.stripTrailingZeros()?.toPlainString(),
)

fun CreateWalletRequestDto.toUserBlockChainWallet(userAccountId: String) = UserBlockChainWallet(
    walletAddress = this.walletAddress,
    currency = this.currency,
    userAccountId = userAccountId,
    description = this.description,
    balance = null,
)

class WalletController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository,
    private val ethService: EthService,
    private val ethWalletAddressValidator: EthWalletAddressValidator,
    private val userBlockChainWalletService: UserBlockChainWalletService,
) : ApiController {

    private companion object : KLogging()


    private fun addMonitoredWallets() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val addWalletsRequest = httpServerExchange.inputStreamToObject(Array<CreateWalletRequestDto>::class.java)
            logger.info { "User $userAccountId is adding wallets: $addWalletsRequest" }
            val duplicatedWalletAddresses = mutableListOf<String>()
            val invalidAddresses = mutableListOf<String>()
            addWalletsRequest.forEach {
                val walletToAdd = it.toUserBlockChainWallet(userAccountId)
                if (!ethWalletAddressValidator.isWalletAddressValid(walletToAdd.walletAddress)) {
                    invalidAddresses += walletToAdd.walletAddress
                } else {
                    try {
                        if (userBlockChainWalletRepository().existsByUserAccountIdAndWalletAddress(userAccountId, walletToAdd.walletAddress)) {
                            duplicatedWalletAddresses += walletToAdd.walletAddress
                        } else {
                            userBlockChainWalletRepository().insertWallet(walletToAdd)
                            val walletBalance = ethService.getEthBalance(walletToAdd.walletAddress)
                            userBlockChainWalletRepository().updateWallet(walletToAdd.copy(balance = walletBalance))
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Could not add wallet $walletToAdd" }
                        throw e
                    }
                }
            }
            if (duplicatedWalletAddresses.isNotEmpty() || invalidAddresses.isNotEmpty()) {
                httpServerExchange.statusCode = 400
                httpServerExchange.responseSender.send(
                    objectMapper.writeValueAsString(
                        CreateWalletsErrorResponseDto(
                            duplicatedAddresses = duplicatedWalletAddresses,
                            invalidAddresses = invalidAddresses,
                        )
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
            if (!ethWalletAddressValidator.isWalletAddressValid(walletUpdateRequest.walletAddress)) {
                httpServerExchange.statusCode = 400
                httpServerExchange.responseSender.send(objectMapper.writeValueAsString(UpdateWalletErrorResponseDto(isAddressInvalid = true)))
            } else try {
                val updateResult = userBlockChainWalletService.updateWallet(userAccountId, walletUpdateRequest)
                if (updateResult.isSuccessful()) {
                    logger.info { "User $userAccountId updated wallet: $walletUpdateRequest" }
                } else {
                    logger.warn { "User $userAccountId dit not update wallet $walletUpdateRequest successfully: $updateResult" }
                    httpServerExchange.statusCode = 400
                    httpServerExchange.responseSender.send(
                        objectMapper.writeValueAsString(
                            UpdateWalletErrorResponseDto(
                                isAddressDuplicated = updateResult.isAddressDuplicated,
                                isIdInvalid = updateResult.isIdInvalid,
                            )
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
            .map { it.toDto() }
        this.responseSender.send(objectMapper.writeValueAsString(wallets))
    }

    private fun HttpServerExchange.sendUserWallet(userAccountId: String, walletId: String) {
        val wallet = userBlockChainWalletRepository().findOneById(walletId).toDto()
        this.responseSender.send(objectMapper.writeValueAsString(wallet))
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
                    httpServerExchange.sendUserWallet(userAccountId, walletId)
                } else {
                    httpServerExchange.statusCode = 404
                }
            }
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
        refreshWalletsBalance(),
        deleteWallet(),
        updateWallet(),
    )
}
