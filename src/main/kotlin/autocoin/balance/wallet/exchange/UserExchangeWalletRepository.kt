package autocoin.balance.wallet.exchange

import autocoin.balance.wallet.blockchain.UserCurrencyBalance
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.kotlin.BindKotlin
import org.jdbi.v3.sqlobject.kotlin.RegisterKotlinMapper
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate

interface UserExchangeWalletRepository {
    @SqlUpdate(
        """
        INSERT INTO user_exchange_wallet (id, user_account_id, exchange, exchange_user_id, currency, balance, amount_in_orders, amount_available)
        VALUES (:id, :userAccountId, :exchange, :exchangeUserId, :currency, :balance, :amountInOrders, :amountAvailable)
        """
    )
    fun insertWallet(@BindKotlin userExchangeWallet: UserExchangeWallet)

    @SqlUpdate( "UPDATE user_exchange_wallet set  balance = :balance WHERE id = :id" )
    fun updateWalletBalance(@BindKotlin userExchangeWallet: UserExchangeWallet): Int

    @SqlQuery("SELECT currency, SUM(balance) as balance FROM user_exchange_wallet WHERE user_account_id = :userAccountId GROUP BY currency")
    @RegisterKotlinMapper(UserCurrencyBalance::class)
    fun selectUserCurrencyBalance(@Bind("userAccountId") userAccountId: String): List<UserCurrencyBalance>

    @SqlQuery("SELECT * FROM user_exchange_wallet WHERE id = :id")
    @RegisterKotlinMapper(UserExchangeWallet::class)
    fun findOneById(@Bind("id") id: String): UserExchangeWallet

    @SqlQuery("SELECT * FROM user_exchange_wallet WHERE user_account_id = :userAccountId")
    @RegisterKotlinMapper(UserExchangeWallet::class)
    fun findManyByUserAccountId(@Bind("userAccountId") userAccountId: String): List<UserExchangeWallet>

    @SqlUpdate("DELETE FROM user_exchange_wallet WHERE user_account_id = :userAccountId")
    fun deleteByUserAccountId(userAccountId: String): Int
}
