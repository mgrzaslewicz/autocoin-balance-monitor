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
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.util.Methods.GET
import io.undertow.util.Methods.POST
import mu.KLogging

data class AddWalletRequestDto(
    val walletAddress: String,
    val currency: String,
    val description: String?,
)

data class AddWalletsErrorResponseDto(
    val duplicatedAddresses: List<String>,
    val invalidAddresses: List<String>,
)

data class WalletResponseDto(
    val walletAddress: String,
    val currency: String,
    val description: String?,
    val balance: String?,
)

fun UserBlockChainWallet.toDto() = WalletResponseDto(
    walletAddress = this.walletAddress,
    currency = this.currency,
    description = this.description,
    balance = this.balance?.stripTrailingZeros()?.toPlainString(),
)

fun AddWalletRequestDto.toUserBlockChainWallet(userAccountId: String) = UserBlockChainWallet(
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
) : ApiController {

    private companion object : KLogging()


    private fun addMonitoredWallets() = object : ApiEndpoint {
        override val method = POST
        override val urlTemplate = "/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            httpServerExchange.startBlocking() // in order to user inputStream
            val addWalletsRequest = objectMapper.readValue(httpServerExchange.inputStream, Array<AddWalletRequestDto>::class.java)
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
                        AddWalletsErrorResponseDto(
                            duplicatedAddresses = duplicatedWalletAddresses,
                            invalidAddresses = invalidAddresses,
                        )
                    )
                )
            }
            logger.info { "User $userAccountId added wallets: $addWalletsRequest" }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    private fun getMonitoredWallets() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/wallets"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.debug { "User $userAccountId is requesting wallets" }
            val wallets = userBlockChainWalletRepository().findWalletsByUserAccountId(userAccountId)
                .map { it.toDto() }
            httpServerExchange.responseSender.send(objectMapper.writeValueAsString(wallets))
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(addMonitoredWallets(), getMonitoredWallets())
}
