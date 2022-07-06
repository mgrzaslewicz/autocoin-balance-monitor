package autocoin.balance.wallet.summary

import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery

interface UserBalanceSummaryRepository {

    @SqlQuery(
        """select distinct(currency) from (
        select distinct currency from user_blockchain_wallet where user_account_id = :userAccountId  
        union 
        select distinct currency from user_exchange_wallet where user_account_id = :userAccountId
        union
        select distinct currency from user_currency_asset where user_account_id = :userAccountId
        ) 
        as currencies order by currency asc"""
    )
    fun findUniqueUserCurrencies(@Bind("userAccountId") userAccountId: String): List<String>

}
