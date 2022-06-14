package autocoin.balance.api.controller

import autocoin.TestDb
import autocoin.TestServer
import autocoin.balance.api.HttpHandlerWrapper
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.app.createJdbi
import autocoin.balance.oauth.server.UserAccount
import autocoin.balance.wallet.UserBlockChainWallet
import autocoin.balance.wallet.UserBlockChainWalletRepository
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

    @BeforeEach
    fun setup() {
        startedDatabase = TestDb.startDatabase()
        jdbi = createJdbi(startedDatabase.datasource)
        walletRepository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
    }

    @AfterEach
    fun cleanup() {
        startedDatabase.container.stop()
    }


    @Test
    fun shouldAddWallets() {
        // given
        val walletController = WalletController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userBlockChainWalletRepository = { walletRepository },
        )
        val startedServer = TestServer.startTestServer(walletController)
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        AddWalletRequestDto(
                            walletAddress = "sample wallet address",
                            currency = "sample currency",
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
        assertThat(addedWallets[0].walletAddress).isEqualTo("sample wallet address")
        assertThat(addedWallets[0].currency).isEqualTo("sample currency")
        assertThat(addedWallets[0].description).isEqualTo("sample description")
    }

    @Test
    fun shouldRespondWithDuplicatedWallets() {
        // given
        val walletController = WalletController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userBlockChainWalletRepository = { walletRepository },
        )
        val startedServer = TestServer.startTestServer(walletController)
        val duplicatedWalletAddress = "sample wallet address"
        val request = Request.Builder()
            .url("http://localhost:${startedServer.port}/wallets")
            .post(
                objectMapper.writeValueAsString(
                    listOf(
                        AddWalletRequestDto(
                            walletAddress = duplicatedWalletAddress,
                            currency = "sample currency",
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
        val duplicatedWalletAddresses = objectMapper.readValue(response.body?.string(), Array<String>::class.java)
        assertThat(duplicatedWalletAddresses).containsExactly(duplicatedWalletAddress)
    }

    @Test
    fun shouldGetWallets() {
        // given
        val expectedWallets = listOf(
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = "sample wallet address 1",
                description = "sample description 1",
                balance = null,
                id = UUID.randomUUID().toString(),
            ),
            UserBlockChainWallet(
                userAccountId = authenticatedHttpHandlerWrapper.userAccountId,
                currency = "ETH",
                walletAddress = "sample wallet address 2",
                description = null,
                balance = BigDecimal("2.78"),
                id = UUID.randomUUID().toString(),
            ),
        )

        walletRepository.insertWallet(expectedWallets[0])
        walletRepository.insertWallet(expectedWallets[1])

        val walletController = WalletController(
            objectMapper = objectMapper,
            oauth2BearerTokenAuthHandlerWrapper = authenticatedHttpHandlerWrapper,
            userBlockChainWalletRepository = { walletRepository },
        )
        val startedServer = TestServer.startTestServer(walletController)
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
            assertThat(walletsResponse[0].walletAddress).isEqualTo("sample wallet address 1")

            assertThat(walletsResponse[1].currency).isEqualTo("ETH")
            assertThat(walletsResponse[1].description).isNull()
            assertThat(walletsResponse[1].walletAddress).isEqualTo("sample wallet address 2")
            assertThat(walletsResponse[1].balance).isEqualTo("2.78")
            assertAll()
        }
    }

}
