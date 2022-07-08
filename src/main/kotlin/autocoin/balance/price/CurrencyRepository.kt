package autocoin.balance.price

import org.jdbi.v3.sqlobject.statement.SqlQuery

interface CurrencyRepository {
    @SqlQuery("select distinct(currency) from (select distinct currency from user_blockchain_wallet union select distinct currency from user_exchange_wallet) as currencies order by currency asc")
    fun selectUniqueWalletCurrencies(): List<String>
}
