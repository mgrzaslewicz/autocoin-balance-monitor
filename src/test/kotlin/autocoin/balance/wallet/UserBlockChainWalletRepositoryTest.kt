package autocoin.balance.wallet

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

class UserBlockChainWalletRepositoryTest {

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
        val userWallets = repository.findWalletsByUserAccountId(userAccountId)
        assertThat(userWallets).hasSize(1)
        assertThat(userWallets.first()).isEqualTo(
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
}
