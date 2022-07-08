package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.blockchain.eth.EthService
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.oauth.server.authorizeWithOauth2
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.util.Methods.GET
import io.undertow.util.PathTemplateMatch

data class EthWalletBalanceResponseDto(
    val ethWalletAddress: String,
    val balance: String,
)

class EthWalletController(
    private val objectMapper: ObjectMapper,
    private val ethService: EthService,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val ethWalletAddressValidator: EthWalletAddressValidator,
) : ApiController {
    private val walletAddressParameter = "address"
    private fun getEthWalletBalance() = object : ApiEndpoint {
        override val method = GET
        override val urlTemplate = "/eth/wallet/{$walletAddressParameter}"


        override val httpHandler = HttpHandler { httpServerExchange ->
            val pathMatch: PathTemplateMatch = httpServerExchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
            val ethWalletAddress = pathMatch.parameters[walletAddressParameter]
            if (ethWalletAddress != null && ethWalletAddressValidator.isWalletAddressValid(ethWalletAddress)) {
                val balance = ethService.getEthBalance(ethWalletAddress)
                if (balance == null) {
                    httpServerExchange.statusCode = 500
                    httpServerExchange.responseSender.send("Could not get wallet balance")
                } else {
                    httpServerExchange.responseSender.send(
                        objectMapper.writeValueAsString(
                            EthWalletBalanceResponseDto(
                                ethWalletAddress = ethWalletAddress,
                                balance = balance.toPlainString(),
                            )
                        )
                    )
                }
            } else {
                httpServerExchange.statusCode = 400
                httpServerExchange.responseSender.send("Incorrect eth wallet address")
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)
    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(getEthWalletBalance())
}
