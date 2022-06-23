package autocoin.balance.wallet.exchange

import autocoin.TestDb
import autocoin.balance.app.createJdbi
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.util.*
import javax.sql.DataSource

class UserExchangeWalletLastRefreshRepositoryIT {
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
        val repository = jdbi.onDemand(UserExchangeWalletLastRefreshRepository::class.java)
        val walletRefreshToInsert = UserExchangeWalletLastRefresh(
            id = walletId,
            userAccountId = userAccountId,
            exchange = "bittrex",
            exchangeUserId = exchangeUserId,
            errorMessage = "API key expired",
            insertTime = Timestamp(System.currentTimeMillis()),
        )
        // when
        repository.insertWalletLastRefresh(walletRefreshToInsert)
        // then
        val userWallets = repository.findManyByUserAccountId(userAccountId)
        assertThat(userWallets).containsOnly(walletRefreshToInsert)
    }

    @Test
    fun shouldDeleteByExchangeUserId() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserExchangeWalletLastRefreshRepository::class.java)
        val walletRefreshToBeDeleted1 = UserExchangeWalletLastRefresh(
            userAccountId = userAccountId,
            exchange = "bittrex",
            exchangeUserId = "exchangeUserId1",
            errorMessage = "API key expired",
            insertTime = Timestamp(System.currentTimeMillis()),
        )
        val walletRefreshToBeDeleted2 = UserExchangeWalletLastRefresh(
            userAccountId = userAccountId,
            exchange = "bittrex",
            exchangeUserId = "exchangeUserId2",
            errorMessage = "API key expired",
            insertTime = Timestamp(System.currentTimeMillis()),
        )
        val walletRefreshNotToBeDeleted = UserExchangeWalletLastRefresh(
            userAccountId = UUID.randomUUID().toString(),
            exchange = "bittrex",
            exchangeUserId = "exchangeUserId3",
            errorMessage = null,
            insertTime = Timestamp(System.currentTimeMillis()),
        )
        repository.insertWalletLastRefresh(walletRefreshToBeDeleted1)
        repository.insertWalletLastRefresh(walletRefreshToBeDeleted2)
        repository.insertWalletLastRefresh(walletRefreshNotToBeDeleted)
        // when
        val howManyDeleted = repository.deleteByUserAccountId(userAccountId)
        // then
        assertThat(howManyDeleted).isEqualTo(2)
        val userWalletsLastRefresh = repository.findManyByUserAccountId(userAccountId)
        assertThat(userWalletsLastRefresh).isEmpty()
    }

}
