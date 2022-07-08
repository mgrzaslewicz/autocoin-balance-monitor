package autocoin.balance.wallet.exchange

import java.math.BigDecimal
import java.sql.Timestamp
import java.util.*

data class UserExchangeWallet(
   val id: String = UUID.randomUUID().toString(),
   val userAccountId: String,
   val exchange: String,
   val exchangeUserId: String,
   val currency: String,
   val balance: BigDecimal,
   val amountInOrders: BigDecimal,
   val amountAvailable: BigDecimal,
)


data class UserExchangeWalletLastRefresh(
   val id: String = UUID.randomUUID().toString(),
   val userAccountId: String,
   val exchange: String,
   val exchangeUserId: String,
   val exchangeUserName: String,
   val errorMessage: String?,
   val insertTime: Timestamp,
)
