package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.blockchain.BlockChainExplorerUrlService
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.price.PriceService
import autocoin.balance.wallet.currency.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods
import io.undertow.util.PathTemplateMatch
import mu.KLogging
import java.math.BigDecimal
import java.util.*

data class UserCurrencyAssetResponseDto(
    val id: String,
    val currency: String,
    val description: String?,
    val walletAddress: String?,
    val blockChainExplorerUrl: String?,
    val balance: String,
    val valueInOtherCurrency: Map<String, String?>,
)

data class UserCurrencyAssetSummaryResponseDto(
    val currency: String,
    val balance: String,
    val valueInOtherCurrency: Map<String, String?>,
    val priceInOtherCurrency: Map<String, String?>,
)

data class UserCurrencyAssetsResponseDto(
    val userCurrencyAssets: List<UserCurrencyAssetResponseDto>,
    val userCurrencyAssetsSummary: List<UserCurrencyAssetSummaryResponseDto>,
)

fun UserCurrencyAssetWithValue.toDto(blockChainExplorerUrl: String?) = UserCurrencyAssetResponseDto(
    id = this.userCurrencyAsset.id,
    currency = this.userCurrencyAsset.currency,
    description = this.userCurrencyAsset.description,
    walletAddress = this.userCurrencyAsset.walletAddress,
    blockChainExplorerUrl = blockChainExplorerUrl,
    balance = this.userCurrencyAsset.balance.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency.toStringValueMap(),
)

fun UserCurrencyAssetSummaryWithPriceAndValue.toDto() = UserCurrencyAssetSummaryResponseDto(
    currency = this.userCurrencyAssetSummary.currency,
    balance = this.userCurrencyAssetSummary.balance.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency.toStringValueMap(),
    priceInOtherCurrency = this.priceInOtherCurrency.toStringValueMap(),
)


data class AddUserCurrencyAssetRequestDto(
    val currency: String,
    val balance: String,
    val description: String?,
    val walletAddress: String?,
) {
    fun toUserCurrencyAsset(userAccountId: String) = UserCurrencyAsset(
        currency = currency,
        description = description,
        balance = balance.toBigDecimal(),
        userAccountId = userAccountId,
        walletAddress = walletAddress,
    )
}

data class UpdateUserCurrencyAssetRequestDto(
    val id: String,
    val currency: String,
    val balance: String,
    val walletAddress: String?,
    val description: String?,
)

data class UpdateUserCurrencyAssetErrorResponseDto(
    val isIdInvalid: Boolean,
    val isBalanceInvalid: Boolean,
)

class UserCurrencyAssetController(
    private val objectMapper: ObjectMapper,
    private val oauth2BearerTokenAuthHandlerWrapper: HttpHandlerWrapper,
    private val userCurrencyAssetService: UserCurrencyAssetService,
    private val userCurrencyAssetRepository: () -> UserCurrencyAssetRepository,
    private val blockChainExplorerUrlService: BlockChainExplorerUrlService,
    private val priceService: PriceService,
) : ApiController {
    private companion object : KLogging()

    private val sampleUserCurrencyAssets by lazy {
        UserCurrencyAssetsResponseDto(
            userCurrencyAssets = listOf(
                UserCurrencyAssetResponseDto(
                    id = UUID.randomUUID().toString(),
                    currency = "BTC",
                    balance = "0.49",
                    description = "from binance",
                    valueInOtherCurrency = mapOf(
                        "USD" to priceService.getUsdValue("BTC", BigDecimal("0.49"))?.toPlainString()
                    ),
                    walletAddress = "bc1qmsyd37rsfc9u29cun77rfc3m7gs72u03m8u7v8aadxaql7346r3sx8p9tr",
                    blockChainExplorerUrl = blockChainExplorerUrlService.getBlockchainExplorerUrl(
                        currency = "BTC",
                        walletAddress = "bc1qmsyd37rsfc9u29cun77rfc3m7gs72u03m8u7v8aadxaql7346r3sx8p9tr"
                    ),
                ),
                UserCurrencyAssetResponseDto(
                    id = UUID.randomUUID().toString(),
                    currency = "BTC",
                    balance = "0.157",
                    description = "at binance",
                    valueInOtherCurrency = mapOf(
                        "USD" to priceService.getUsdValue("BTC", BigDecimal("0.157"))?.toPlainString()
                    ),
                    walletAddress = null,
                    blockChainExplorerUrl = null,
                ),
                UserCurrencyAssetResponseDto(
                    id = UUID.randomUUID().toString(),
                    currency = "ETH",
                    balance = "1.29818",
                    description = "deployed at https://app.yield.app",
                    valueInOtherCurrency = mapOf(
                        "USD" to priceService.getUsdValue("ETH", BigDecimal("1.29818"))?.toPlainString()
                    ),
                    walletAddress = null,
                    blockChainExplorerUrl = null,
                )
            ),
            userCurrencyAssetsSummary = listOf(
                UserCurrencyAssetSummaryResponseDto(
                    currency = "BTC",
                    balance = "0.647",
                    valueInOtherCurrency = mapOf(
                        "USD" to priceService.getUsdValue("BTC", BigDecimal("0.647"))?.toPlainString()
                    ),
                    priceInOtherCurrency = mapOf(
                        "USD" to priceService.getPrice("BTC", "USD")?.price?.toPlainString()
                    ),
                ),
                UserCurrencyAssetSummaryResponseDto(
                    currency = "ETH",
                    balance = "1.29818",
                    valueInOtherCurrency = mapOf(
                        "USD" to priceService.getUsdValue("ETH", BigDecimal("1.29818"))?.toPlainString()
                    ),
                    priceInOtherCurrency = mapOf(
                        "USD" to priceService.getPrice("ETH", "USD")?.price?.toPlainString()
                    ),
                ),
            ),
        )
    }

    private fun getSampleUserCurrencyAssets() = object : ApiEndpoint {
        override val method = Methods.GET
        override val urlTemplate = "/user-currency-assets/sample"

        override val httpHandler = HttpHandler { httpServerExchange ->
            httpServerExchange.responseSender.send(objectMapper.writeValueAsString(sampleUserCurrencyAssets))
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    private fun getUserCurrencyAssets() = object : ApiEndpoint {
        override val method = Methods.GET
        override val urlTemplate = "/user-currency-assets"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val userCurrencyAssetSummaryList = userCurrencyAssetService.getUserCurrencyAssetsSummary(userAccountId)
            val userCurrencyAssets = userCurrencyAssetService.getUserCurrencyAssets(userAccountId)
            val result = UserCurrencyAssetsResponseDto(
                userCurrencyAssets = userCurrencyAssets.map {
                    it.toDto(blockChainExplorerUrlService.getBlockchainExplorerUrl(it.userCurrencyAsset))
                },
                userCurrencyAssetsSummary = userCurrencyAssetSummaryList.map { it.toDto() }
            )
            httpServerExchange.responseSender.send(objectMapper.writeValueAsString(result))
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    private fun getUserCurrencyAsset() = object : ApiEndpoint {
        private val userCurrencyAssetIdParameter = "userCurrencyAssetId"

        override val method = Methods.GET
        override val urlTemplate = "/user-currency-assets/{$userCurrencyAssetIdParameter}"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val pathMatch: PathTemplateMatch = httpServerExchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
            val userCurrencyAssetId = pathMatch.parameters[userCurrencyAssetIdParameter]
            if (userCurrencyAssetId != null) {
                val result = userCurrencyAssetService.getUserCurrencyAsset(userAccountId, userCurrencyAssetId)
                if (result != null) {
                    httpServerExchange.responseSender.send(
                        objectMapper.writeValueAsString(
                            result.toDto(
                                blockChainExplorerUrlService.getBlockchainExplorerUrl(result.userCurrencyAsset)
                            )
                        )
                    )
                } else {
                    httpServerExchange.statusCode = 404
                }
            } else {
                httpServerExchange.statusCode = 404
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    private fun deleteUserCurrencyAsset() = object : ApiEndpoint {
        private val userCurrencyAssetIdParameter = "userCurrencyAssetId"

        override val method = Methods.DELETE
        override val urlTemplate = "/user-currency-assets/{$userCurrencyAssetIdParameter}"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val pathMatch: PathTemplateMatch = httpServerExchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
            val userCurrencyAssetId = pathMatch.parameters[userCurrencyAssetIdParameter]

            if (userCurrencyAssetId != null) {
                val howManyDeleted = userCurrencyAssetRepository().deleteOneByUserAccountIdAndId(
                    userAccountId = userAccountId,
                    id = userCurrencyAssetId
                )
                if (howManyDeleted == 0) {
                    logger.warn { "User $userAccountId tried to delete user currency asset $userCurrencyAssetId which was not found. That might be a hack attempt or just missing entry" }
                    httpServerExchange.statusCode = 404
                }
            } else {
                httpServerExchange.statusCode = 404
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    private fun <T> HttpServerExchange.inputStreamToObject(clazz: Class<T>): T {
        this.startBlocking() // in order to user inputStream
        return objectMapper.readValue(this.inputStream, clazz)
    }

    private fun updateUserCurrencyAsset() = object : ApiEndpoint {
        private val userCurrencyAssetIdParameter = "userCurrencyAssetId"

        override val method = Methods.PUT
        override val urlTemplate = "/user-currency-assets/{$userCurrencyAssetIdParameter}"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val pathMatch: PathTemplateMatch = httpServerExchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)
            val userCurrencyAssetId = pathMatch.parameters[userCurrencyAssetIdParameter]
            val updateUserCurrencyAssetRequest =
                httpServerExchange.inputStreamToObject(UpdateUserCurrencyAssetRequestDto::class.java)
            logger.info { "User $userAccountId is updating currency asset $updateUserCurrencyAssetRequest" }
            if (userCurrencyAssetId != null) {
                val userCurrencyAsset =
                    userCurrencyAssetRepository().findOneByUserAccountIdAndId(userAccountId, userCurrencyAssetId)
                if (userCurrencyAsset != null) {
                    val modifiedCurrencyAsset = userCurrencyAsset.copy(
                        balance = BigDecimal(updateUserCurrencyAssetRequest.balance),
                        description = updateUserCurrencyAssetRequest.description,
                        currency = updateUserCurrencyAssetRequest.currency,
                        walletAddress = updateUserCurrencyAssetRequest.walletAddress,
                    )
                    val howManyUpdated = userCurrencyAssetRepository().updateCurrencyAsset(modifiedCurrencyAsset)
                    if (howManyUpdated != 1) {
                        logger.error { "User $userAccountId tried to update user currency asset $userCurrencyAssetId but operation failed, got no $howManyUpdated assets instead of 1" }
                        httpServerExchange.statusCode = 500
                    }
                } else {
                    logger.warn { "User $userAccountId tried to update user currency asset $userCurrencyAssetId which was not found. That might be an attempt to modify someone else data or just missing entry" }
                    httpServerExchange.statusCode = 404
                }
            }
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    private fun addUserCurrencyAssets() = object : ApiEndpoint {

        override val method = Methods.POST
        override val urlTemplate = "/user-currency-assets"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            logger.info { "User $userAccountId is adding currency asset, about to deserialize the request body" }
            val addUserCurrencyAssetRequests =
                httpServerExchange.inputStreamToObject(Array<AddUserCurrencyAssetRequestDto>::class.java)
            logger.info { "User $userAccountId is adding currency assets $addUserCurrencyAssetRequests" }
            userCurrencyAssetRepository().insertCurrencyAssets(addUserCurrencyAssetRequests.map {
                it.toUserCurrencyAsset(
                    userAccountId
                )
            })
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getUserCurrencyAsset(),
        getUserCurrencyAssets(),
        getSampleUserCurrencyAssets(),
        deleteUserCurrencyAsset(),
        deleteUserCurrencyAsset(),
        updateUserCurrencyAsset(),
        addUserCurrencyAssets(),
    )
}
