package autocoin.balance.api.controller

import autocoin.StartedServer
import autocoin.TestDb
import autocoin.TestServer
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.app.createJdbi
import autocoin.balance.blockchain.eth.EthService
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.oauth.server.UserAccount
import autocoin.balance.wallet.UserBlockChainWallet
import autocoin.balance.wallet.UserBlockChainWalletRepository
import autocoin.balance.wallet.UserBlockChainWalletService
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
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
import java.math.BigDecimal
import java.util.*

class WalletControllerIT {

    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()
    private val sampleEthAddress1 = "0x19ce8df35f56bcabb8426d145b8e7984bef90a22"
    private val sampleEthAddress2 = "0x19ce8df35f56bcabb8426d145b8e7984bef90a23"

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
    private lateinit var ethService: EthService
    private lateinit var walletController: WalletController
    private lateinit var walletService: UserBlockChainWalletService
    private lateinit var startedServer: StartedServer

    @BeforeEach
    fun setup() {
        startedDatabase = TestDb.startDatabase()
        jdbi = createJdbi(startedDatabase.datasource)
        walletRepository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        ethService = mock()
        walletService = UserBlockChainWalletService(
            userBlockChainWalletRepository = { walletRepository },
            ethService = ethService
        )
        walletController = WalletController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userBlockChainWalletRepository = { walletRepository },
            ethWalletAddressValidator = EthWalletAddressValidator(),
            ethService = ethService,
            userBlockChainWalletService = walletService,
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
        whenever(ethService.getEthBalance(any())).thenReturn(BigDecimal("0.56"))
        startedServer = TestServer.startTestServer(walletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        AddWalletRequestDto(
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
        val addedWallets = walletRepository.findWalletsByUserAccountId(authenticatedHttpHandlerWrapper.userAccountId)
        assertThat(addedWallets).hasSize(1)
        assertThat(addedWallets[0].walletAddress).isEqualTo(sampleEthAddress1)
        assertThat(addedWallets[0].currency).isEqualTo("ETH")
        assertThat(addedWallets[0].balance).isEqualTo("0.56")
        assertThat(addedWallets[0].description).isEqualTo("sample description")
    }

    @Test
    fun shouldRespondWithDuplicatedWallets() {
        // given
        whenever(ethService.getEthBalance(any())).thenReturn(BigDecimal("0.56"))
        startedServer = TestServer.startTestServer(walletController)
        val duplicatedWalletAddress = sampleEthAddress1
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        AddWalletRequestDto(
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
        val addWalletsErrorResponse = objectMapper.readValue(response.body?.string(), AddWalletsErrorResponseDto::class.java)
        assertThat(addWalletsErrorResponse.duplicatedAddresses).containsExactly(duplicatedWalletAddress)
    }

    @Test
    fun shouldRespondWithInvalidWallets() {
        // given
        whenever(ethService.getEthBalance(any())).thenReturn(BigDecimal("0.56"))
        startedServer = TestServer.startTestServer(walletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        AddWalletRequestDto(
                            walletAddress = "invalid address 1",
                            currency = "ETH",
                            description = "sample description"
                        ),
                        AddWalletRequestDto(
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
        val addWalletsErrorResponse = objectMapper.readValue(response.body?.string(), AddWalletsErrorResponseDto::class.java)
        assertThat(addWalletsErrorResponse.invalidAddresses).containsExactly("invalid address 1", "invalid address 2")
    }

    @Test
    fun shouldGetWallets() {
        // given
        whenever(ethService.getEthBalance(any())).thenReturn(BigDecimal("0.56"))
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

        startedServer = TestServer.startTestServer(walletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets")
            .get()
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.isSuccessful).isTrue
        val walletsResponse = objectMapper.readValue(response.body?.string(), Array<WalletResponseDto>::class.java)
        SoftAssertions().apply {
            assertThat(walletsResponse).hasSize(2)
            assertThat(walletsResponse[0].currency).isEqualTo("ETH")
            assertThat(walletsResponse[0].description).isEqualTo("sample description 1")
            assertThat(walletsResponse[0].walletAddress).isEqualTo(sampleEthAddress1)

            assertThat(walletsResponse[1].currency).isEqualTo("ETH")
            assertThat(walletsResponse[1].description).isNull()
            assertThat(walletsResponse[1].walletAddress).isEqualTo(sampleEthAddress2)
            assertThat(walletsResponse[1].balance).isEqualTo("2.78")
            assertAll()
        }
    }

    @Test
    fun shouldRefreshWalletsBalances() {
        // given
        whenever(ethService.getEthBalance(any())).thenReturn(BigDecimal("0.56"))
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

        startedServer = TestServer.startTestServer(walletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets/balance/refresh")
            .post(EMPTY_REQUEST)
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        assertThat(response.isSuccessful).isTrue
        val walletsResponse = objectMapper.readValue(response.body?.string(), Array<WalletResponseDto>::class.java)
        SoftAssertions().apply {
            assertThat(walletsResponse).hasSize(2)
            assertThat(walletsResponse[0].balance).isEqualTo("0.56")
            assertThat(walletsResponse[1].balance).isEqualTo("0.56")
            assertAll()
        }
    }

}
