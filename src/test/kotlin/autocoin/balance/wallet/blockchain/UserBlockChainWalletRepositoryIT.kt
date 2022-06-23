package autocoin.balance.wallet.blockchain

import autocoin.TestDb
import autocoin.balance.app.createJdbi
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.UnableToExecuteStatementException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.*
import javax.sql.DataSource

class UserBlockChainWalletRepositoryIT {

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
    fun shouldInsertUserBlockChainWallet() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        // when
        repository.insertWallet(
            UserBlockChainWallet(
                id = id,
                walletAddress = "test",
                currency = "ETH",
                userAccountId = userAccountId,
                description = "sample description",
                balance = BigDecimal("10.6"),
            )
        )
        // then
        val userWallets = repository.findManyByUserAccountId(userAccountId)
        assertThat(userWallets).containsOnly(
            UserBlockChainWallet(
                id = id,
                userAccountId = userAccountId,
                currency = "ETH",
                walletAddress = "test",
                description = "sample description",
                balance = BigDecimal("10.6"),
            )
        )
    }

    @Test
    fun shouldSelectUserCurrencyBalance() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val ethWallet = UserBlockChainWallet(
            id = UUID.randomUUID().toString(),
            walletAddress = "test1",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        // when
        repository.insertWallet(ethWallet)
        repository.insertWallet(ethWallet.copy(id = UUID.randomUUID().toString(), walletAddress = "test2", balance = BigDecimal("10.0")))
        repository.insertWallet(ethWallet.copy(id = UUID.randomUUID().toString(), walletAddress = "test3", balance = null, currency = "BTC"))
        repository.insertWallet(ethWallet.copy(id = UUID.randomUUID().toString(), walletAddress = "test4", balance = BigDecimal("15.8"), currency = "BTC"))
        // then
        val currencyBalances = repository.selectUserCurrencyBalance(userAccountId)
        assertThat(currencyBalances).containsExactlyInAnyOrder(
            UserCurrencyBalance(currency = "ETH", balance = BigDecimal("20.6")),
            UserCurrencyBalance(currency = "BTC", balance = BigDecimal("15.8"))
        )
    }

    @Test
    fun shouldUpdateUserBlockChainWallet() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val wallet = UserBlockChainWallet(
            id = id,
            walletAddress = "test",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        repository.insertWallet(wallet)
        // when
        val howManyUpdated = repository.updateWallet(wallet.copy(walletAddress = "test2", currency = "BTC", description = "new description", balance = BigDecimal("11.5")))
        // then
        val updatedWallet = repository.findOneById(id)
        assertThat(howManyUpdated).isEqualTo(1)
        assertThat(updatedWallet.walletAddress).isEqualTo("test2")
        assertThat(updatedWallet.currency).isEqualTo("BTC")
        assertThat(updatedWallet.description).isEqualTo("new description")
        assertThat(updatedWallet.balance).isEqualTo(BigDecimal("11.5"))
    }

    @Test
    fun shouldFindWalletById() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val wallet = UserBlockChainWallet(
            id = id,
            walletAddress = "test",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        repository.insertWallet(wallet)
        // when
        val foundWallet = repository.findOneById(id)
        // then
        assertThat(foundWallet.id).isEqualTo(id)
    }

    @Test
    fun shouldWalletExistByUserAccountIdAndWalletAddress() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val wallet = UserBlockChainWallet(
            id = id,
            walletAddress = "test",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        repository.insertWallet(wallet)
        // when
        val walletExists = repository.existsByUserAccountIdAndWalletAddress(userAccountId, "test")
        // then
        assertThat(walletExists).isTrue
    }

    @Test
    fun shouldWalletExistByUserAccountIdAndId() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val wallet = UserBlockChainWallet(
            id = id,
            walletAddress = "test",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        repository.insertWallet(wallet)
        // when
        val walletExists = repository.existsByUserAccountIdAndId(userAccountId, id)
        // then
        assertThat(walletExists).isTrue
    }

    @Test
    fun shouldDeleteWallet() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val wallet = UserBlockChainWallet(
            id = id,
            walletAddress = "test",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        repository.insertWallet(wallet)
        // when
        val howManyDeleted = repository.deleteOneByUserAccountIdAndWalletAddress(userAccountId, "test")
        // then
        assertThat(howManyDeleted).isEqualTo(1)
        assertThat(repository.existsByUserAccountIdAndWalletAddress(userAccountId, "test")).isFalse
    }

    @Test
    fun shouldRejectNonUniqueWallet() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val repository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val newWallet = UserBlockChainWallet(
            id = id,
            walletAddress = "test",
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
        )
        repository.insertWallet(newWallet)
        // when-then
        assertThrows<UnableToExecuteStatementException> { repository.insertWallet(newWallet) }
    }
}
