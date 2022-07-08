package autocoin.balance.api.controller

import autocoin.balance.api.ApiController
import autocoin.balance.api.ApiEndpoint
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.oauth.server.authorizeWithOauth2
import autocoin.balance.oauth.server.userAccountId
import autocoin.balance.wallet.currency.*
import com.fasterxml.jackson.databind.ObjectMapper
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.Methods
import io.undertow.util.PathTemplateMatch
import mu.KLogging
import java.math.BigDecimal

data class UserCurrencyAssetResponseDto(
    val id: String,
    val currency: String,
    val description: String?,
    val balance: String,
    val valueInOtherCurrency: Map<String, String?>,
)

data class UserCurrencyAssetSummaryResponseDto(
    val currency: String,
    val balance: String,
    val valueInOtherCurrency: Map<String, String?>,
)

data class UserCurrencyAssetsResponseDto(
    val userCurrencyAssets: List<UserCurrencyAssetResponseDto>,
    val userCurrencyAssetsSummary: List<UserCurrencyAssetSummaryResponseDto>,
)

fun UserCurrencyAssetWithValue.toDto() = UserCurrencyAssetResponseDto(
    id = this.userCurrencyAsset.id,
    currency = this.userCurrencyAsset.currency,
    description = this.userCurrencyAsset.description,
    balance = this.userCurrencyAsset.balance.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency.map { entry -> entry.key to entry.value?.toPlainString() }.toMap(),
)

fun UserCurrencyAssetSummaryWithValue.toDto() = UserCurrencyAssetSummaryResponseDto(
    currency = this.userCurrencyAssetSummary.currency,
    balance = this.userCurrencyAssetSummary.balance.toPlainString(),
    valueInOtherCurrency = this.valueInOtherCurrency.map { entry -> entry.key to entry.value?.toPlainString() }.toMap(),
)


data class AddUserCurrencyAssetRequestDto(
    val currency: String,
    val balance: String,
    val description: String?,
) {
    fun toUserCurrencyAsset(userAccountId: String) = UserCurrencyAsset(
        currency = currency,
        description = description,
        balance = balance.toBigDecimal(),
        userAccountId = userAccountId,
    )
}

data class UpdateUserCurrencyAssetRequestDto(
    val id: String,
    val currency: String,
    val balance: String,
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
) : ApiController {
    private companion object : KLogging()

    fun getUserCurrencyAssets() = object : ApiEndpoint {
        override val method = Methods.GET
        override val urlTemplate = "/user-currency-assets"

        override val httpHandler = HttpHandler { httpServerExchange ->
            val userAccountId = httpServerExchange.userAccountId()
            val userCurrencyAssetSummaryList = userCurrencyAssetService.getUserCurrencyAssetsSummary(userAccountId)
            val userCurrencyAssets = userCurrencyAssetService.getUserCurrencyAssets(userAccountId)
            val result = UserCurrencyAssetsResponseDto(
                userCurrencyAssets = userCurrencyAssets.map { it.toDto() },
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
                    httpServerExchange.responseSender.send(objectMapper.writeValueAsString(result.toDto()))
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
                val howManyDeleted = userCurrencyAssetRepository().deleteOneByUserAccountIdAndId(userAccountId = userAccountId, id = userCurrencyAssetId)
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
            val updateUserCurrencyAssetRequest = httpServerExchange.inputStreamToObject(UpdateUserCurrencyAssetRequestDto::class.java)
            if (userCurrencyAssetId != null) {
                val userCurrencyAsset = userCurrencyAssetRepository().findOneByUserAccountIdAndId(userAccountId, userCurrencyAssetId)
                if (userCurrencyAsset != null) {
                    val modifiedCurrencyAsset = userCurrencyAsset.copy(
                        balance = BigDecimal(updateUserCurrencyAssetRequest.balance),
                        description = updateUserCurrencyAssetRequest.description,
                        currency = updateUserCurrencyAssetRequest.currency,
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
            val addUserCurrencyAssetRequests = httpServerExchange.inputStreamToObject(Array<AddUserCurrencyAssetRequestDto>::class.java)
            logger.info { "User $userAccountId is adding currency assets $addUserCurrencyAssetRequests" }
            userCurrencyAssetRepository().insertCurrencyAssets(addUserCurrencyAssetRequests.map { it.toUserCurrencyAsset(userAccountId) })
        }.authorizeWithOauth2(oauth2BearerTokenAuthHandlerWrapper)

    }

    override fun apiEndpoints(): List<ApiEndpoint> = listOf(
        getUserCurrencyAsset(),
        getUserCurrencyAssets(),
        deleteUserCurrencyAsset(),
        deleteUserCurrencyAsset(),
        updateUserCurrencyAsset(),
        addUserCurrencyAssets(),
    )
}
