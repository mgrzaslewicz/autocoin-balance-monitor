package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
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

fun AddWalletRequestDto.toUserBlockChainWallet(userAccountId: String) =    UserBlockChainWallet(
    walletAddress = this.walletAddress,
    currency = this.currency,
    userAccountId = userAccountId,
    description = this.description,
    balance = null,
)

class WalletController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository
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
            addWalletsRequest.forEach {
                userBlockChainWalletRepository().insertWallet(it.toUserBlockChainWallet(userAccountId))
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
