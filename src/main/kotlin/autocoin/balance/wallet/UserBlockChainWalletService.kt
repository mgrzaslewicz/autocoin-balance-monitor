package autocoin.balance.wallet

import autocoin.balance.blockchain.eth.EthService
import mu.KLogging

class UserBlockChainWalletService(
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository,
    private val ethService: EthService
) {

    companion object : KLogging()

    fun refreshWalletBalances(userAccountId: String) {
        val userWallets = userBlockChainWalletRepository().findManyByUserAccountId(userAccountId)
        userWallets.forEach { wallet ->
            val newBalance = ethService.getEthBalance(wallet.walletAddress)
            if (newBalance != null) {
                userBlockChainWalletRepository().updateWallet(wallet.copy(balance = newBalance))
            }
        }
    }
}
