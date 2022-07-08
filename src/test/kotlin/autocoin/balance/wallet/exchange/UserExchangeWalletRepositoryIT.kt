package autocoin.balance.wallet.exchange

import autocoin.TestDb
import autocoin.balance.app.createJdbi
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import javax.sql.DataSource

class UserExchangeWalletRepositoryIT {
    private lateinit var startedDatabase: TestDb.StartedDatabase
    private lateinit var datasource: DataSource
    private lateinit var jdbi: Jdbi

    @BeforeEach
    fun setup() {
        startedDatabase = TestDb.startDatabase()
        datasource = startedDatabase.datasource
        jdbi = createJdbi(datasource)
    }

    @AfterEach
    fun cleanup() {
        startedDatabase.container.stop()
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

}
