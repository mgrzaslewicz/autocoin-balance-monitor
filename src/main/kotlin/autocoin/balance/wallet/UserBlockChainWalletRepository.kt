package autocoin.balance.wallet

import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface UserBlockChainWalletRepository {
    @SqlUpdate(
        """
        INSERT INTO user_blockchain_wallet (id, user_account_id, wallet_address, currency, balance, description)
        VALUES (:id, :userAccountId, :walletAddress, :currency, :balance, :description)
        """
    )
    fun insertWallet(@BindKotlin userBlockChainWallet: UserBlockChainWallet)

    @SqlQuery("SELECT * FROM user_blockchain_wallet WHERE user_account_id = :userAccountId")
    @RegisterKotlinMapper(UserBlockChainWallet::class)
    fun findWalletsByUserAccountId(@Bind("userAccountId") userAccountId: String): List<UserBlockChainWallet>
}
