package autocoin.balance.wallet.exchange

import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface UserExchangeWalletLastRefreshRepository {
    @SqlUpdate(
        """
        INSERT INTO user_exchange_wallet_last_refresh (id, user_account_id, exchange, exchange_user_id, error_message, insert_time)
        VALUES (:id, :userAccountId, :exchange, :exchangeUserId, :errorMessage, :insertTime)
        """
    )
    fun insertWalletLastRefresh(@BindKotlin userExchangeWalletLastRefresh: UserExchangeWalletLastRefresh)

    @SqlQuery("SELECT * FROM user_exchange_wallet_last_refresh WHERE user_account_id = :userAccountId")
    @RegisterKotlinMapper(UserExchangeWalletLastRefresh::class)
    fun findManyByUserAccountId(@Bind("userAccountId") userAccountId: String): List<UserExchangeWalletLastRefresh>

    @SqlUpdate("DELETE FROM user_exchange_wallet_last_refresh WHERE user_account_id = :userAccountId")
    fun deleteByUserAccountId(@Bind("userAccountId") userAccountId: String): Int
}
