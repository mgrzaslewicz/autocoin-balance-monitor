package autocoin.balance.api.controller

import autocoin.StartedServer
import autocoin.TestServer
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.blockchain.BlockChainExplorerUrlService
import autocoin.balance.wallet.currency.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
class UserCurrencyAssetControllerIT {

    @Mock
    private lateinit var userCurrencyAssetService: UserCurrencyAssetService

    @Mock
    private lateinit var userCurrencyAssetRepository: UserCurrencyAssetRepository

    @Mock
    private lateinit var blockChainExplorerUrlService: BlockChainExplorerUrlService

    private val authenticatedHttpHandlerWrapper = AuthenticatedHttpHandlerWrapper()
    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()
    private val sampleUserCurrencyAsset = UserCurrencyAsset(
        id = UUID.randomUUID().toString(),
        userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
        currency = "BTC",
        balance = BigDecimal.ONE,
        description = "sample description",
        walletAddress = "sample wallet address",
    )

    private lateinit var startedServer: StartedServer
    private lateinit var userCurrencyAssetController: UserCurrencyAssetController

    @BeforeEach
    fun setup() {
        userCurrencyAssetController = UserCurrencyAssetController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userCurrencyAssetService = userCurrencyAssetService,
            userCurrencyAssetRepository = { userCurrencyAssetRepository },
            blockChainExplorerUrlService = blockChainExplorerUrlService,
        )
        startedServer = TestServer.startTestServer(userCurrencyAssetController)
    }

    @AfterEach
    fun cleanup() {
        startedServer.stop()
    }

    private fun setupSampleResultsInCurrencyAssetService(userCurrencyAssetId: String) {
        whenever(userCurrencyAssetService.getUserCurrencyAssets(authenticatedHttpHandlerWrapper.userAccountId))
            .thenReturn(
                listOf(
                    UserCurrencyAssetWithValue(
                        userCurrencyAsset = sampleUserCurrencyAsset.copy(id = userCurrencyAssetId),
                        valueInOtherCurrency = mapOf("USD" to BigDecimal.TEN),
                    )
                )
            )
        whenever(userCurrencyAssetService.getUserCurrencyAssetsSummary(authenticatedHttpHandlerWrapper.userAccountId))
            .thenReturn(
                listOf(
                    UserCurrencyAssetSummaryWithPriceAndValue(
                        userCurrencyAssetSummary = UserCurrencyAssetSummary(
                            currency = "BTC",
                            balance = BigDecimal.ONE,
                        ),
                        valueInOtherCurrency = mapOf("USD" to BigDecimal.TEN),
                        priceInOtherCurrency = mapOf("USD" to BigDecimal("2.4")),
                    )
                )
            )

    }

    @Test
    fun shouldGetUserCurrencyAssets() {
        // given
        whenever(blockChainExplorerUrlService.getBlockchainExplorerUrl(any<UserCurrencyAsset>())).thenReturn("wallet url")
        val userCurrencyAssetId = UUID.randomUUID().toString()
        val expectedResponse = UserCurrencyAssetsResponseDto(
            userCurrencyAssets = listOf(
                UserCurrencyAssetResponseDto(
                    id = userCurrencyAssetId,
                    currency = "BTC",
                    balance = "1",
                    description = "sample description",
                    valueInOtherCurrency = mapOf("USD" to "10"),
                    walletAddress = "sample wallet address",
                    blockChainExplorerUrl = "wallet url",
                )
            ),
            userCurrencyAssetsSummary = listOf(
                UserCurrencyAssetSummaryResponseDto(
                    currency = "BTC",
                    balance = "1",
                    valueInOtherCurrency = mapOf("USD" to "10"),
                    priceInOtherCurrency = mapOf("USD" to "2.4"),
                )
            )
        )
        setupSampleResultsInCurrencyAssetService(userCurrencyAssetId)
        // when
        val request = Request.Builder()
            .url(startedServer.uri.resolve("/user-currency-assets").toURL())
            .get()
            .build()
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val userCurrencyAssetsResponseDto = objectMapper.readValue(response.body?.string(), UserCurrencyAssetsResponseDto::class.java)
        assertThat(userCurrencyAssetsResponseDto).isEqualTo(expectedResponse)
    }

    @Test
    fun shouldGetUserCurrencyAsset() {
        // given
        whenever(userCurrencyAssetService.getUserCurrencyAsset(authenticatedHttpHandlerWrapper.userAccountId, sampleUserCurrencyAsset.id)).thenReturn(
            UserCurrencyAssetWithValue(userCurrencyAsset = sampleUserCurrencyAsset, valueInOtherCurrency = mapOf())
        )
        whenever(blockChainExplorerUrlService.getBlockchainExplorerUrl(sampleUserCurrencyAsset)).thenReturn("wallet url")
        // when
        val request = Request.Builder()
            .url(startedServer.uri.resolve("/user-currency-assets/${sampleUserCurrencyAsset.id}").toURL())
            .get()
            .build()
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val userCurrencyAssetsResponseDto = objectMapper.readValue(response.body?.string(), UserCurrencyAssetResponseDto::class.java)
        assertThat(userCurrencyAssetsResponseDto).isEqualTo(
            UserCurrencyAssetResponseDto(
                id = sampleUserCurrencyAsset.id,
                currency = sampleUserCurrencyAsset.currency,
                balance = sampleUserCurrencyAsset.balance.toPlainString(),
                walletAddress = sampleUserCurrencyAsset.walletAddress,
                blockChainExplorerUrl = "wallet url",
                description = sampleUserCurrencyAsset.description,
                valueInOtherCurrency = mapOf(),
            )
        )
    }

    @Test
    fun shouldDeleteUserCurrencyAsset() {
        // given
        val userCurrencyAssetId = UUID.randomUUID().toString()
        whenever(userCurrencyAssetRepository.deleteOneByUserAccountIdAndId(authenticatedHttpHandlerWrapper.userAccountId, userCurrencyAssetId)).thenReturn(1)
        // when
        val request = Request.Builder()
            .url(startedServer.uri.resolve("/user-currency-assets/$userCurrencyAssetId").toURL())
            .delete()
            .build()
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        verify(userCurrencyAssetRepository).deleteOneByUserAccountIdAndId(authenticatedHttpHandlerWrapper.userAccountId, userCurrencyAssetId)
    }

    @Test
    fun shouldUpdateUserCurrencyAsset() {
        // given
        whenever(userCurrencyAssetRepository.findOneByUserAccountIdAndId(authenticatedHttpHandlerWrapper.userAccountId, sampleUserCurrencyAsset.id))
            .thenReturn(sampleUserCurrencyAsset)
        whenever(userCurrencyAssetRepository.updateCurrencyAsset(any())).thenReturn(1)
        val updateUserCurrencyAssetRequestDto = UpdateUserCurrencyAssetRequestDto(
            id = sampleUserCurrencyAsset.id,
            currency = "NEW",
            walletAddress = "new wallet address",
            description = "new description",
            balance = "5678.21",
        )
        // when
        val request = Request.Builder()
            .url(startedServer.uri.resolve("/user-currency-assets/${sampleUserCurrencyAsset.id}").toURL())
            .put(objectMapper.writeValueAsString(updateUserCurrencyAssetRequestDto).toRequestBody())
            .build()
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        verify(userCurrencyAssetRepository).updateCurrencyAsset(
            sampleUserCurrencyAsset.copy(
                currency = "NEW",
                walletAddress = "new wallet address",
                description = "new description",
                balance = BigDecimal("5678.21"),
            )
        )
    }

    @Test
    fun shouldAddUserCurrencyAsset() {
        // given
        val addUserCurrencyAssetRequestDto = AddUserCurrencyAssetRequestDto(
            currency = "NEW",
            description = "new description",
            balance = "5678.21",
            walletAddress = "sample wallet address",
        )
        // when
        val request = Request.Builder()
            .url(startedServer.uri.resolve("/user-currency-assets").toURL())
            .post(objectMapper.writeValueAsString(listOf(addUserCurrencyAssetRequestDto)).toRequestBody())
            .build()
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        verify(userCurrencyAssetRepository).insertCurrencyAssets(
            argThat {
                val currencyAsset = this.first()
                currencyAsset.currency == addUserCurrencyAssetRequestDto.currency
                        && currencyAsset.balance == addUserCurrencyAssetRequestDto.balance.toBigDecimal()
                        && currencyAsset.description == addUserCurrencyAssetRequestDto.description
                        && currencyAsset.walletAddress == addUserCurrencyAssetRequestDto.walletAddress
                        && currencyAsset.userAccountId == authenticatedHttpHandlerWrapper.userAccountId
            }
        )
    }

}
