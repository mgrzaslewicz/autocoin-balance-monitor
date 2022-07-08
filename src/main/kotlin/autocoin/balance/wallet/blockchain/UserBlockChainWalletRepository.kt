package autocoin.balance.wallet.blockchain

import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.math.BigDecimal

data class UserCurrencyBalance(
    val currency: String,
    val balance: BigDecimal?,
)

interface UserBlockChainWalletRepository {
    @SqlUpdate(
        """
        INSERT INTO user_blockchain_wallet (id, user_account_id, wallet_address, currency, balance, description)
        VALUES (:id, :userAccountId, :walletAddress, :currency, :balance, :description)
        """
    )
    fun insertWallet(@BindKotlin userBlockChainWallet: UserBlockChainWallet)

    @SqlUpdate(
        """
        UPDATE user_blockchain_wallet set currency = :currency, wallet_address = :walletAddress, balance = :balance, description = :description
        WHERE id = :id
        """
    )
    fun updateWallet(@BindKotlin userBlockChainWallet: UserBlockChainWallet): Int

    @SqlQuery("SELECT * FROM user_blockchain_wallet WHERE user_account_id = :userAccountId")
    @RegisterKotlinMapper(UserBlockChainWallet::class)
    fun findManyByUserAccountId(@Bind("userAccountId") userAccountId: String): List<UserBlockChainWallet>

    @SqlQuery("SELECT * FROM user_blockchain_wallet WHERE id = :id")
    @RegisterKotlinMapper(UserBlockChainWallet::class)
    fun findOneById(@Bind("id") id: String): UserBlockChainWallet

    @SqlQuery("SELECT EXISTS(SELECT 1 FROM user_blockchain_wallet WHERE id = :id)")
    @RegisterKotlinMapper(UserBlockChainWallet::class)
    fun existsById(@Bind("id") id: String): Boolean

    @SqlQuery("SELECT EXISTS (select 1 FROM user_blockchain_wallet WHERE user_account_id = :userAccountId and wallet_address = :walletAddress)")
    fun existsByUserAccountIdAndWalletAddress(@Bind("userAccountId") userAccountId: String, @Bind("walletAddress") walletAddress: String): Boolean

    @SqlQuery("SELECT EXISTS (select 1 FROM user_blockchain_wallet WHERE user_account_id = :userAccountId and id = :id)")
    fun existsByUserAccountIdAndId(@Bind("userAccountId") userAccountId: String, @Bind("id") id: String): Boolean

    @SqlUpdate("DELETE FROM user_blockchain_wallet WHERE user_account_id = :userAccountId and wallet_address = :walletAddress")
    fun deleteOneByUserAccountIdAndWalletAddress(@Bind("userAccountId") userAccountId: String, @Bind("walletAddress") walletAddress: String): Int

    @SqlQuery("SELECT currency, SUM(balance) as balance FROM user_blockchain_wallet WHERE user_account_id = :userAccountId GROUP BY currency")
    @RegisterKotlinMapper(UserCurrencyBalance::class)
    fun selectUserCurrencyBalance(@Bind("userAccountId") userAccountId: String): List<UserCurrencyBalance>
}
