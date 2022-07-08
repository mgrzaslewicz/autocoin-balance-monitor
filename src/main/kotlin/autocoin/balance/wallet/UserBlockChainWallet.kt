package autocoin.balance.wallet

import java.math.BigDecimal
import java.util.UUID

data class UserBlockChainWallet(
   val id: String = UUID.randomUUID().toString(),
   val userAccountId: String,
   val walletAddress: String,
   val currency: String,
   val balance: BigDecimal? = null,
   val description: String? = null,
)
