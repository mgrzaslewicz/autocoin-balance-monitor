package autocoin.balance.wallet.blockchain

import java.math.BigDecimal
import java.util.*

data class UserBlockChainWallet(
   val id: String = UUID.randomUUID().toString(),
   val userAccountId: String,
   val walletAddress: String,
   val currency: String,
   val balance: BigDecimal?,
   val description: String?,
)
