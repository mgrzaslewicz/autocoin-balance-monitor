package autocoin.balance.blockchain

import java.math.BigDecimal

interface BlockchainWalletService {
    fun getBalance(walletAddress: String): BigDecimal?
    val currency: String
}

class MultiBlockchainWalletService(blockchainWalletServices: List<BlockchainWalletService>) {
    private val blockchainWalletServiceMap: Map<String, BlockchainWalletService> = blockchainWalletServices.associateBy { it.currency }
    fun getBalance(currency: String, walletAddress: String): BigDecimal? {
        return blockchainWalletServiceMap.getValue(currency.uppercase()).getBalance(walletAddress)
    }
}
