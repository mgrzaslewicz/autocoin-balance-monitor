package autocoin.balance.wallet.currency

import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.math.BigDecimal
import java.util.*

data class UserCurrencyAsset(
    val id: String = UUID.randomUUID().toString(),
    val userAccountId: String,
    val currency: String,
    val balance: BigDecimal,
    val description: String?,
    val walletAddress: String?,
)

data class UserCurrencyAssetSummary(
    val currency: String,
    val balance: BigDecimal,
)

interface UserCurrencyAssetRepository {
    @SqlUpdate(
        """
        INSERT INTO user_currency_asset (id, user_account_id, currency, balance, description, wallet_address)
        VALUES (:id, :userAccountId, :currency, :balance, :description, :walletAddress)
        """
    )
    fun insertCurrencyAsset(@BindKotlin userCurrencyAsset: UserCurrencyAsset): Int

    fun insertCurrencyAssets(userCurrencyAssets: List<UserCurrencyAsset>) = userCurrencyAssets
        .fold(0) { acc, it -> acc + insertCurrencyAsset(it) }

    @SqlUpdate("UPDATE user_currency_asset set  currency = :currency, balance = :balance, description = :description , wallet_address = :walletAddress WHERE id = :id")
    fun updateCurrencyAsset(@BindKotlin userCurrencyAsset: UserCurrencyAsset): Int

    @SqlQuery("SELECT currency, SUM(balance) as balance FROM user_currency_asset WHERE user_account_id = :userAccountId GROUP BY currency")
    @RegisterKotlinMapper(UserCurrencyAssetSummary::class)
    fun selectUserCurrencyAssetSummary(@Bind("userAccountId") userAccountId: String): List<UserCurrencyAssetSummary>

    @SqlQuery("SELECT * FROM user_currency_asset WHERE id = :id")
    @RegisterKotlinMapper(UserCurrencyAsset::class)
    fun findOneById(@Bind("id") id: String): UserCurrencyAsset

    @SqlQuery("SELECT * FROM user_currency_asset WHERE user_account_id = :userAccountId")
    @RegisterKotlinMapper(UserCurrencyAsset::class)
    fun findManyByUserAccountId(@Bind("userAccountId") userAccountId: String): List<UserCurrencyAsset>

    @SqlQuery("SELECT * FROM user_currency_asset WHERE user_account_id = :userAccountId AND currency = :currency")
    @RegisterKotlinMapper(UserCurrencyAsset::class)
    fun findManyByUserAccountIdAndCurrency(@Bind("userAccountId") userAccountId: String, @Bind("currency") currency: String): List<UserCurrencyAsset>

    @SqlUpdate("DELETE FROM user_currency_asset WHERE user_account_id = :userAccountId")
    fun deleteByUserAccountId(userAccountId: String): Int

    @SqlQuery("SELECT EXISTS (select 1 FROM user_currency_asset WHERE user_account_id = :userAccountId and id = :id)")
    fun existsByUserAccountIdAndId(@Bind("userAccountId") userAccountId: String, @Bind("id") id: String): Boolean

    @SqlUpdate("DELETE FROM user_currency_asset WHERE user_account_id = :userAccountId and id = :id")
    fun deleteOneByUserAccountIdAndId(@Bind("userAccountId") userAccountId: String, @Bind("id") id: String): Int

    @SqlQuery("SELECT * FROM user_currency_asset WHERE id = :id and user_account_id = :userAccountId")
    @RegisterKotlinMapper(UserCurrencyAsset::class)
    fun findOneByUserAccountIdAndId(@Bind("userAccountId") userAccountId: String, @Bind("id") id: String): UserCurrencyAsset?
}
