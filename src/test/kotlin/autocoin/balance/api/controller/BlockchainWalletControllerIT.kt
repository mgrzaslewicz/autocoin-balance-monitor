package autocoin.balance.api.controller

import autocoin.StartedServer
import autocoin.TestDb
import autocoin.TestServer
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.app.createJdbi
import autocoin.balance.blockchain.MultiBlockchainWalletService
import autocoin.balance.blockchain.MultiWalletAddressValidator
import autocoin.balance.blockchain.btc.BtcWalletAddressValidator
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.oauth.server.UserAccount
import autocoin.balance.price.PriceService
import autocoin.balance.wallet.blockchain.UserBlockChainWallet
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.blockchain.UserBlockChainWalletService
import io.undertow.security.api.AuthenticationMechanism
import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome
import io.undertow.security.api.AuthenticationMechanism.ChallengeResult
import io.undertow.security.api.AuthenticationMode
import io.undertow.security.api.SecurityContext
import io.undertow.security.handlers.AuthenticationCallHandler
import io.undertow.security.handlers.AuthenticationConstraintHandler
import io.undertow.security.handlers.AuthenticationMechanismsHandler
import io.undertow.security.handlers.SecurityInitialHandler
import io.undertow.security.idm.Account
import io.undertow.security.idm.Credential
import io.undertow.security.idm.IdentityManager
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.util.StatusCodes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
class BlockchainWalletControllerIT {

    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()
    private val sampleEthAddress1 = "0x19ce8df35f56bcabb8426d145b8e7984bef90a22"
    private val sampleEthAddress2 = "0x19ce8df35f56bcabb8426d145b8e7984bef90a23"
    private val sampleBtcAddress1 = "bc1qhq66uyw53n7sfk200czg556mdmdg8t7nvgdkdd"

    class AuthenticatedHttpHandlerWrapper : HttpHandlerWrapper {
        private val userAccount = UserAccount(
            userName = "some-user@non-existing-domain",
            userAccountId = UUID.randomUUID().toString(),
            authorities = emptySet(),
        )
        val userAccountId = userAccount.userAccountId

        override fun wrap(next: HttpHandler): HttpHandler {
            return SecurityInitialHandler(
                AuthenticationMode.PRO_ACTIVE,
                object : IdentityManager {
                    override fun verify(account: Account) = account
                    override fun verify(id: String?, credential: Credential?) = null
                    override fun verify(credential: Credential?) = null
                },
                AuthenticationMechanismsHandler(
                    AuthenticationConstraintHandler(AuthenticationCallHandler(next)),
                    listOf(object : AuthenticationMechanism {
                        override fun authenticate(exchange: HttpServerExchange?, securityContext: SecurityContext?): AuthenticationMechanismOutcome {
                            securityContext?.authenticationComplete(userAccount, "test authentication", false)
                            return AuthenticationMechanismOutcome.AUTHENTICATED
                        }

                        override fun sendChallenge(exchange: HttpServerExchange?, securityContext: SecurityContext?): ChallengeResult {
                            return ChallengeResult(true, StatusCodes.UNAUTHORIZED)
                        }
                    })
                )
            )
        }
    }

    private val authenticatedHttpHandlerWrapper = AuthenticatedHttpHandlerWrapper()

    private lateinit var startedDatabase: TestDb.StartedDatabase
    private lateinit var jdbi: Jdbi
    private lateinit var walletRepository: UserBlockChainWalletRepository

    @Mock
    private lateinit var multiBlockchainWalletService: MultiBlockchainWalletService
    private lateinit var blockchainWalletController: BlockchainWalletController
    private lateinit var walletService: UserBlockChainWalletService
    private lateinit var startedServer: StartedServer

    @Mock
    private lateinit var priceService: PriceService

    @BeforeEach
    fun setup() {
        startedDatabase = TestDb.startDatabase()
        jdbi = createJdbi(startedDatabase.datasource)
        walletRepository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        walletService = UserBlockChainWalletService(
            userBlockChainWalletRepository = { walletRepository },
            multiBlockchainWalletService = multiBlockchainWalletService
        )
        blockchainWalletController = BlockchainWalletController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userBlockChainWalletRepository = { walletRepository },
            walletAddressValidator = MultiWalletAddressValidator(
                walletAddressValidators = listOf(BtcWalletAddressValidator(), EthWalletAddressValidator())
            ),
            userBlockChainWalletService = walletService,
            priceService = priceService,
        )
    }

    @AfterEach
    fun cleanup() {
        startedDatabase.container.stop()
        startedServer.stop()
    }


    @Test
    fun shouldAddWallets() {
        // given
        whenever(multiBlockchainWalletService.getBalance(any(), any())).thenReturn(BigDecimal("0.56"))
        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        CreateBlockchainWalletRequestDto(
                            walletAddress = sampleEthAddress1,
                            currency = "ETH",
                            description = "sample description"
                        )
                    )
                ).toRequestBody()
            )
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val addedWallets = walletRepository.findManyByUserAccountId(authenticatedHttpHandlerWrapper.userAccountId)
        assertThat(addedWallets).hasSize(1)
        assertThat(addedWallets[0].walletAddress).isEqualTo(sampleEthAddress1)
        assertThat(addedWallets[0].currency).isEqualTo("ETH")
        assertThat(addedWallets[0].balance).isEqualTo("0.56")
        assertThat(addedWallets[0].description).isEqualTo("sample description")
    }

    @Test
    fun shouldRespondWithDuplicatedWallets() {
        // given
        whenever(multiBlockchainWalletService.getBalance(any(), any())).thenReturn(BigDecimal("0.56"))
        startedServer = TestServer.startTestServer(blockchainWalletController)
        val duplicatedWalletAddress = sampleEthAddress1
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        CreateBlockchainWalletRequestDto(
                            walletAddress = duplicatedWalletAddress,
                            currency = "ETH",
                            description = "sample description"
                        )
                    )
                ).toRequestBody()
            )
            .build()
        httpClientWithoutAuthorization.newCall(request).execute()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(400)
        val addWalletsErrorResponse = objectMapper.readValue(response.body?.string(), CreateBlockchainWalletsErrorResponseDto::class.java)
        assertThat(addWalletsErrorResponse.duplicatedAddresses).containsExactly(duplicatedWalletAddress)
    }

    @Test
    fun shouldRespondWithInvalidWallets() {
        // given
        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        CreateBlockchainWalletRequestDto(
                            walletAddress = "invalid address 1",
                            currency = "ETH",
                            description = "sample description"
                        ),
                        CreateBlockchainWalletRequestDto(
                            walletAddress = "invalid address 2",
                            currency = "ETH",
                            description = "sample description"
                        )
                    )
                ).toRequestBody()
            )
            .build()
        httpClientWithoutAuthorization.newCall(request).execute()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(400)
        val addWalletsErrorResponse = objectMapper.readValue(response.body?.string(), CreateBlockchainWalletsErrorResponseDto::class.java)
        assertThat(addWalletsErrorResponse.invalidAddresses).containsExactly("invalid address 1", "invalid address 2")
    }

    @Test
    fun shouldGetWallets() {
        // given
        whenever(priceService.getUsdValueOrNull(eq("ETH"), eq(BigDecimal("2.78")))).thenReturn(BigDecimal("10.5"))
        val expectedWallets = listOf(
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = sampleEthAddress1,
                description = "sample description 1",
                balance = null,
                id = UUID.randomUUID().toString(),
            ),
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = sampleEthAddress2,
                description = null,
                balance = BigDecimal("2.78"),
                id = UUID.randomUUID().toString(),
            ),
        )

        walletRepository.insertWallet(expectedWallets[0])
        walletRepository.insertWallet(expectedWallets[1])

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val walletsResponse = objectMapper.readValue(response.body?.string(), Array<BlockchainWalletResponseDto>::class.java)
        SoftAssertions().apply {
            assertThat(walletsResponse).hasSize(2)
            assertThat(walletsResponse[0].currency).isEqualTo("ETH")
            assertThat(walletsResponse[0].description).isEqualTo("sample description 1")
            assertThat(walletsResponse[0].walletAddress).isEqualTo(sampleEthAddress1)
            assertThat(walletsResponse[0].usdBalance).isNull()

            assertThat(walletsResponse[1].currency).isEqualTo("ETH")
            assertThat(walletsResponse[1].description).isNull()
            assertThat(walletsResponse[1].walletAddress).isEqualTo(sampleEthAddress2)
            assertThat(walletsResponse[1].balance).isEqualTo("2.78")
            assertThat(walletsResponse[1].usdBalance).isEqualTo("10.5")
            assertAll()
        }
    }

    @Test
    fun shouldGetCurrencyBalance() {
        // given
        whenever(priceService.getUsdValueOrNull("ETH", BigDecimal("5.65"))).thenReturn(BigDecimal("2000.0"))
        val expectedWallets = listOf(
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = sampleEthAddress1,
                description = "sample description 1",
                balance = BigDecimal("2.5"),
                id = UUID.randomUUID().toString(),
            ),
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = sampleEthAddress2,
                description = null,
                balance = BigDecimal("3.15"),
                id = UUID.randomUUID().toString(),
            ),
        )

        walletRepository.insertWallet(expectedWallets[0])
        walletRepository.insertWallet(expectedWallets[1])

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets/currency/balance")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val walletsResponse = objectMapper.readValue(response.body?.string(), Array<UserCurrencyBalanceResponseDto>::class.java)
        SoftAssertions().apply {
            assertThat(walletsResponse).hasSize(1)
            assertThat(walletsResponse[0].currency).isEqualTo("ETH")
            assertThat(walletsResponse[0].balance).isEqualTo("5.65")
            assertThat(walletsResponse[0].usdBalance).isEqualTo("2000")
            assertAll()
        }
    }

    @Test
    fun shouldGetWallet() {
        // given
        val walletId = UUID.randomUUID().toString()
        val wallet = UserBlockChainWallet(
            userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
            currency = "ETH",
            walletAddress = sampleEthAddress1,
            description = "sample description 1",
            balance = null,
            id = walletId,
        )

        walletRepository.insertWallet(wallet)

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets/$walletId")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val walletsResponse = objectMapper.readValue(response.body?.string(), BlockchainWalletResponseDto::class.java)
        SoftAssertions().apply {
            assertThat(walletsResponse.currency).isEqualTo("ETH")
            assertThat(walletsResponse.description).isEqualTo("sample description 1")
            assertThat(walletsResponse.walletAddress).isEqualTo(sampleEthAddress1)
            assertAll()
        }
    }

    @Test
    fun shouldReturn404WhenGetNonExistingWallet() {
        // given
        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets/nonexistingwallet")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(404)
    }

    @Test
    fun shouldReturn404WhenGetWalletBelongingToAnotherUser() {
        // given
        val userAccountOfSomeOtherUserNotMakingTheRequest = UUID.randomUUID().toString()
        val walletId = UUID.randomUUID().toString()
        val wallet = UserBlockChainWallet(
            userAccountId = userAccountOfSomeOtherUserNotMakingTheRequest,
            currency = "ETH",
            walletAddress = sampleEthAddress1,
            description = "sample description 1",
            balance = null,
            id = walletId,
        )

        walletRepository.insertWallet(wallet)

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets/$walletId")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(404)
    }

    @Test
    fun shouldRefreshWalletsBalances() {
        // given
        whenever(multiBlockchainWalletService.getBalance(any(), any())).thenReturn(BigDecimal("0.56"))
        val expectedWallets = listOf(
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = sampleEthAddress1,
                description = "sample description 1",
                balance = null,
                id = UUID.randomUUID().toString(),
            ),
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = sampleEthAddress2,
                description = null,
                balance = BigDecimal("2.78"),
                id = UUID.randomUUID().toString(),
            ),
        )

        walletRepository.insertWallet(expectedWallets[0])
        walletRepository.insertWallet(expectedWallets[1])

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallets/balance/refresh")
            .post(EMPTY_REQUEST)
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val walletsResponse = objectMapper.readValue(response.body?.string(), Array<BlockchainWalletResponseDto>::class.java)
        SoftAssertions().apply {
            assertThat(walletsResponse).hasSize(2)
            assertThat(walletsResponse[0].balance).isEqualTo("0.56")
            assertThat(walletsResponse[1].balance).isEqualTo("0.56")
            assertAll()
        }
    }

    @Test
    fun shouldUpdateWallet() {
        // given
        whenever(multiBlockchainWalletService.getBalance(any(), any())).thenReturn(BigDecimal("0.56"))
        val walletId = UUID.randomUUID().toString()
        val expectedWallet = UserBlockChainWallet(
            userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
            currency = "ETH",
            walletAddress = sampleEthAddress1,
            description = "sample description 1",
            balance = null,
            id = walletId,
        )

        walletRepository.insertWallet(expectedWallet)

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallet")
            .put(
                objectMapper.writeValueAsString(
                    UpdateBlockchainWalletRequestDto(
                        id = walletId,
                        walletAddress = sampleBtcAddress1,
                        description = "new description",
                        currency = "BTC",
                    )
                ).toRequestBody()
            )
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        val updatedWallet = walletRepository.findOneById(walletId)
        assertThat(updatedWallet.walletAddress).isEqualTo(sampleBtcAddress1)
        assertThat(updatedWallet.description).isEqualTo("new description")
        assertThat(updatedWallet.currency).isEqualTo("BTC")
        assertThat(updatedWallet.balance).isEqualTo(BigDecimal("0.56"))
    }

    @Test
    fun shouldDeleteWallet() {
        // given
        val walletToRemove = UserBlockChainWallet(
            userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
            currency = "ETH",
            walletAddress = sampleEthAddress1,
            description = "sample description 1",
            balance = null,
            id = UUID.randomUUID().toString(),
        )

        walletRepository.insertWallet(walletToRemove)

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/blockchain/wallet/$sampleEthAddress1")
            .delete(EMPTY_REQUEST)
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(200)
        assertThat(walletRepository.existsByUserAccountIdAndWalletAddress(userAccountId = authenticatedHttpHandlerWrapper.userAccountId, sampleEthAddress1)).isFalse
    }

    @Test
    fun shouldReturn404WhenDeletingNonExistingWallet() {
        // given
        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallet/nonexistingwallet")
            .delete(EMPTY_REQUEST)
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(404)
    }

    @Test
    fun shouldReturn404WhenDeletingWalletBelongingToAnotherUser() {
        // given
        val walletId = UUID.randomUUID().toString()
        val userAccountOfSomeOtherUserNotMakingTheRequest = UUID.randomUUID().toString()
        val wallet = UserBlockChainWallet(
            userAccountId = userAccountOfSomeOtherUserNotMakingTheRequest,
            currency = "ETH",
            walletAddress = sampleEthAddress1,
            description = "sample description 1",
            balance = null,
            id = walletId,
        )

        walletRepository.insertWallet(wallet)

        startedServer = TestServer.startTestServer(blockchainWalletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallet/$walletId")
            .delete(EMPTY_REQUEST)
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.code).isEqualTo(404)
    }

}
