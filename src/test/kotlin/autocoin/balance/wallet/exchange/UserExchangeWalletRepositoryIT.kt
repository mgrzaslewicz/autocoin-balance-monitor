package autocoin.balance.wallet.exchange

import autocoin.TestDb
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.*
import java.math.BigDecimal
import java.util.*

class UserExchangeWalletRepositoryIT {
    private lateinit var jdbi: Jdbi

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
            startedDatabase.container.stop()
        }
    }

    @BeforeEach
    fun setup() {
        jdbi = startedDatabase.jdbi
    }

    @AfterEach
    fun cleanup() {
        jdbi.useHandle<Exception> { handle ->
            handle.execute("""select 'drop table "' || tablename || '" cascade;' from pg_tables;""")
        }
    }

    @Test
    fun shouldInsertUserExchangeWallet() {
        // given
        val walletId = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val exchangeUserId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserExchangeWalletRepository::class.java)
        val walletToInsert = UserExchangeWallet(
            id = walletId,
            currency = "ETH",
            userAccountId = userAccountId,
            balance = BigDecimal("10.6"),
            exchange = "bittrex",
            exchangeUserId = exchangeUserId,
            amountInOrders = BigDecimal("1.7"),
            amountAvailable = BigDecimal("8.9"),
        )
        // when
        repository.insertWallet(walletToInsert)
        // then
        val userWallets = repository.findManyByUserAccountId(userAccountId)
        assertThat(userWallets).containsOnly(walletToInsert)
    }

    @Test
    fun shouldDeleteByExchangeUserId() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val exchangeUserId1 = UUID.randomUUID().toString()
        val exchangeUserId2 = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserExchangeWalletRepository::class.java)
        val walletToBeDeleted1 = UserExchangeWallet(
            id = UUID.randomUUID().toString(),
            currency = "ETH",
            userAccountId = userAccountId,
            balance = BigDecimal("10.6"),
            exchange = "bittrex",
            exchangeUserId = exchangeUserId1,
            amountInOrders = BigDecimal("1.7"),
            amountAvailable = BigDecimal("8.9"),
        )
        val walletToBeDeleted2 = UserExchangeWallet(
            id = UUID.randomUUID().toString(),
            currency = "BTC",
            userAccountId = userAccountId,
            balance = BigDecimal("15.6"),
            exchange = "bittrex",
            exchangeUserId = exchangeUserId1,
            amountInOrders = BigDecimal("1.8"),
            amountAvailable = BigDecimal("8.8"),
        )
        val walletNotToBeDeleted = UserExchangeWallet(
            id = UUID.randomUUID().toString(),
            currency = "BTC",
            userAccountId = UUID.randomUUID().toString(),
            balance = BigDecimal("15.6"),
            exchange = "bittrex",
            exchangeUserId = exchangeUserId2,
            amountInOrders = BigDecimal("1.9"),
            amountAvailable = BigDecimal("8.7"),
        )
        repository.insertWallet(walletToBeDeleted1)
        repository.insertWallet(walletToBeDeleted2)
        repository.insertWallet(walletNotToBeDeleted)
        // when
        val howManyDeleted = repository.deleteByUserAccountId(userAccountId)
        // then
        assertThat(howManyDeleted).isEqualTo(2)
        val userWallets = repository.findManyByUserAccountId(userAccountId)
        assertThat(userWallets).isEmpty()
    }

    @Test
    fun shouldFindManyByUserAccountIdAndCurrency() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserExchangeWalletRepository::class.java)
        val wallet1 = UserExchangeWallet(
            currency = "ETH",
            userAccountId = userAccountId,
            balance = BigDecimal("10.6"),
            exchange = "bittrex",
            exchangeUserId = UUID.randomUUID().toString(),
            amountInOrders = BigDecimal("1.7"),
            amountAvailable = BigDecimal("8.9"),
        )
        val wallet2 = UserExchangeWallet(
            currency = "BTC",
            userAccountId = userAccountId,
            balance = BigDecimal("15.6"),
            exchange = "bittrex",
            exchangeUserId = UUID.randomUUID().toString(),
            amountInOrders = BigDecimal("1.8"),
            amountAvailable = BigDecimal("8.8"),
        )
        val wallet3 = UserExchangeWallet(
            currency = "BTC",
            userAccountId = UUID.randomUUID().toString(),
            balance = BigDecimal("15.6"),
            exchange = "bittrex",
            exchangeUserId = UUID.randomUUID().toString(),
            amountInOrders = BigDecimal("1.9"),
            amountAvailable = BigDecimal("8.7"),
        )
        val wallet4 = UserExchangeWallet(
            currency = "BTC",
            userAccountId = userAccountId,
            balance = BigDecimal("15.6"),
            exchange = "bittrex",
            exchangeUserId = UUID.randomUUID().toString(),
            amountInOrders = BigDecimal("1.9"),
            amountAvailable = BigDecimal("8.7"),
        )
        repository.insertWallet(wallet1)
        repository.insertWallet(wallet2)
        repository.insertWallet(wallet3)
        repository.insertWallet(wallet4)
        // when
        val result = repository.findManyByUserAccountIdAndCurrency(userAccountId, "BTC")
        // then
        assertThat(result).containsOnly(wallet2, wallet4)
    }

}
