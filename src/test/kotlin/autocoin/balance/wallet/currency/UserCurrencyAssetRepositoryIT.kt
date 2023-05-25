package autocoin.balance.wallet.currency

import autocoin.TestDb
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.math.BigDecimal
import java.util.*

class UserCurrencyAssetRepositoryIT {

    private lateinit var tested: UserCurrencyAssetRepository

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
        tested = startedDatabase.jdbi.onDemand(UserCurrencyAssetRepository::class.java)
        startedDatabase.runMigrations()
    }

    @AfterEach
    fun cleanup() {
        startedDatabase.removeAllTables()
    }

    @Test
    fun shouldInsertUserCurrencyAsset() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val currencyAssetToInsert = UserCurrencyAsset(
            id = id,
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        // when
        tested.insertCurrencyAsset(currencyAssetToInsert)
        // then
        val userWallets = tested.findManyByUserAccountId(userAccountId)
        assertThat(userWallets).containsOnly(currencyAssetToInsert)
    }

    @Test
    fun shouldSelectUserCurrencyAssetsSummary() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val currencyAsset = UserCurrencyAsset(
            id = UUID.randomUUID().toString(),
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        val userCurrencyAssetsToInsert = listOf(
            currencyAsset,
            currencyAsset.copy(id = UUID.randomUUID().toString(), balance = BigDecimal("10.0")),
            currencyAsset.copy(id = UUID.randomUUID().toString(), balance = BigDecimal("20.5"), currency = "BTC"),
            currencyAsset.copy(id = UUID.randomUUID().toString(), balance = BigDecimal("15.8"), currency = "BTC"),
            currencyAsset.copy(id = UUID.randomUUID().toString(), balance = BigDecimal("1.2"), currency = "ABC"),
        )
        // when
        userCurrencyAssetsToInsert.forEach { tested.insertCurrencyAsset(it) }
        // then
        val currencyBalances = tested.selectUserCurrencyAssetSummary(userAccountId)
        assertThat(currencyBalances).containsOnly(
            UserCurrencyAssetSummary(currency = "BTC", balance = BigDecimal("36.3")),
            UserCurrencyAssetSummary(currency = "ETH", balance = BigDecimal("20.6")),
            UserCurrencyAssetSummary(currency = "ABC", balance = BigDecimal("1.2")),
        )
    }

    @Test
    fun shouldUpdateUserCurrencyAsset() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val currencyAsset = UserCurrencyAsset(
            id = id,
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        tested.insertCurrencyAsset(currencyAsset)
        // when
        val howManyUpdated = tested.updateCurrencyAsset(
            currencyAsset.copy(
                currency = "BTC",
                description = "new description",
                balance = BigDecimal("11.5")
            )
        )
        // then
        val updatedWallet = tested.findOneById(id)
        assertThat(howManyUpdated).isEqualTo(1)
        assertThat(updatedWallet.currency).isEqualTo("BTC")
        assertThat(updatedWallet.description).isEqualTo("new description")
        assertThat(updatedWallet.balance).isEqualTo(BigDecimal("11.5"))
    }

    @Test
    fun shouldFindUserCurrencyAssetById() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val userCurrencyAsset = UserCurrencyAsset(
            id = id,
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        tested.insertCurrencyAsset(userCurrencyAsset)
        // when
        val foundWallet = tested.findOneById(id)
        // then
        assertThat(foundWallet.id).isEqualTo(id)
    }

    @Test
    fun shouldUserCurrencyAssetExistByUserAccountIdAndId() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val currencyAsset = UserCurrencyAsset(
            id = id,
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        tested.insertCurrencyAsset(currencyAsset)
        // when
        val exists = tested.existsByUserAccountIdAndId(userAccountId, id)
        // then
        assertThat(exists).isTrue
    }

    @Test
    fun shouldFindUserCurrencyAssetByUserAccountIdAndId() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val currencyAsset = UserCurrencyAsset(
            id = id,
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        tested.insertCurrencyAsset(currencyAsset)
        // when
        val userCurrencyAsset = tested.findOneByUserAccountIdAndId(userAccountId, id)
        // then
        assertThat(userCurrencyAsset).isNotNull
    }

    @Test
    fun shouldNotFindUserCurrencyAssetByUserAccountIdAndId() {
        // when
        val userCurrencyAsset =
            tested.findOneByUserAccountIdAndId("not-existing-user-account-id", "not-existing-user-currency-asset-id")
        // then
        assertThat(userCurrencyAsset).isNull()
    }

    @Test
    fun shouldDeleteUserCurrencyAsset() {
        // given
        val id = UUID.randomUUID().toString()
        val userAccountId = UUID.randomUUID().toString()
        val userCurrencyAsset = UserCurrencyAsset(
            id = id,
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address",
        )
        tested.insertCurrencyAsset(userCurrencyAsset)
        // when
        val howManyDeleted = tested.deleteOneByUserAccountIdAndId(userAccountId, id)
        // then
        assertThat(howManyDeleted).isEqualTo(1)
        assertThat(tested.existsByUserAccountIdAndId(userAccountId, id)).isFalse
    }

    @Test
    fun shouldFindUserCurrencyAssetsByUserAccountIdAndCurrency() {
        // given
        val userAccountId = UUID.randomUUID().toString()
        val currencyAsset1 = UserCurrencyAsset(
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description",
            balance = BigDecimal("10.6"),
            walletAddress = "sample wallet address1",
        )
        val currencyAsset2 = UserCurrencyAsset(
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description2",
            balance = BigDecimal("12.6"),
            walletAddress = "sample wallet address2",
        )
        val currencyAsset3 = UserCurrencyAsset(
            currency = "ETH",
            userAccountId = userAccountId,
            description = "sample description3",
            balance = BigDecimal("14.6"),
            walletAddress = "sample wallet address3",
        )
        val currencyAsset4 = UserCurrencyAsset(
            currency = "BTC",
            userAccountId = userAccountId,
            description = null,
            balance = BigDecimal("45.7"),
            walletAddress = "sample wallet address4",
        )

        tested.insertCurrencyAssets(listOf(currencyAsset1, currencyAsset2, currencyAsset3, currencyAsset4))
        // when
        val result = tested.findManyByUserAccountIdAndCurrency(userAccountId, "ETH")
        // then
        assertThat(result).containsOnly(currencyAsset1, currencyAsset2, currencyAsset3)
    }
}
