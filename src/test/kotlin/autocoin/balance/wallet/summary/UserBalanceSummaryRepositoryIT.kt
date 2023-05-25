package autocoin.balance.wallet.summary

import autocoin.TestDb
import autocoin.balance.wallet.blockchain.UserBlockChainWallet
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWallet
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.*
import java.math.BigDecimal
import java.util.*

class UserBalanceSummaryRepositoryIT {
    private lateinit var jdbi: Jdbi
    private lateinit var tested: UserBalanceSummaryRepository
    private lateinit var exchangeWalletRepository: UserExchangeWalletRepository
    private lateinit var blockchainWalletRepository: UserBlockChainWalletRepository

    private val doesNotMatter = BigDecimal.ONE
    private val userAccountId = UUID.randomUUID().toString()

    companion object {
        private lateinit var startedDatabase: TestDb.StartedDatabase

        @BeforeAll
        @JvmStatic
        fun startDb() {
            startedDatabase = TestDb.startDatabase()
        }

        @AfterAll
        @JvmStatic
        fun stopDb() {
            startedDatabase.stop()
        }
    }


    @BeforeEach
    fun setup() {
        jdbi = startedDatabase.jdbi
        startedDatabase.runMigrations()
        tested = jdbi.onDemand(UserBalanceSummaryRepository::class.java)
        exchangeWalletRepository = jdbi.onDemand(UserExchangeWalletRepository::class.java)
        blockchainWalletRepository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
    }

    @AfterEach
    fun cleanup() {
        jdbi.useHandle<Exception> { handle ->
            handle.execute("""select 'drop table "' || tablename || '" cascade;' from pg_tables;""")
        }
    }

    @Test
    fun shouldSelectNoUserCurrencies() {
        assertThat(tested.findUniqueUserCurrencies("xxx")).isEmpty()
    }

    private fun addSampleBalances() {
        exchangeWalletRepository.insertWallet(
            UserExchangeWallet(
                id = UUID.randomUUID().toString(),
                currency = "ETH",
                userAccountId = userAccountId,
                balance = BigDecimal("10.6"),
                exchange = "bittrex",
                exchangeUserId = "exchange-user-1",
                amountInOrders = doesNotMatter,
                amountAvailable = doesNotMatter,
            )
        )
        exchangeWalletRepository.insertWallet(
            UserExchangeWallet(
                id = UUID.randomUUID().toString(),
                currency = "ETH",
                userAccountId = userAccountId,
                balance = BigDecimal("15.1"),
                exchange = "binance",
                exchangeUserId = "exchange-user-1",
                amountInOrders = doesNotMatter,
                amountAvailable = doesNotMatter,
            )
        )
        exchangeWalletRepository.insertWallet(
            UserExchangeWallet(
                id = UUID.randomUUID().toString(),
                currency = "BTC",
                userAccountId = userAccountId,
                balance = BigDecimal("15.1"),
                exchange = "binance",
                exchangeUserId = "exchange-user-2",
                amountInOrders = doesNotMatter,
                amountAvailable = doesNotMatter,
            )
        )
        blockchainWalletRepository.insertWallet(
            UserBlockChainWallet(
                userAccountId = userAccountId,
                walletAddress = "wallet-1",
                currency = "BTC",
                balance = null,
                description = null
            )
        )
        blockchainWalletRepository.insertWallet(
            UserBlockChainWallet(
                userAccountId = userAccountId,
                walletAddress = "wallet-2",
                currency = "ABC",
                balance = null,
                description = null
            )
        )
    }

    @Test
    fun shouldSelectUserCurrencies() {
        // given
        addSampleBalances()
        // when
        val userCurrencies = tested.findUniqueUserCurrencies(userAccountId)
        // then
        assertThat(userCurrencies).containsOnly("ABC", "BTC", "ETH")
    }
}
