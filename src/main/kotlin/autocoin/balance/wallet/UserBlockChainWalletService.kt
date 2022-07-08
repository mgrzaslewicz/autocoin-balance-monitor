package autocoin.balance.wallet

import autocoin.balance.api.controller.UpdateWalletRequestDto
import autocoin.balance.blockchain.eth.EthService
import mu.KLogging

data class WalletUpdateResult(
    val userHasNoWalletWithGivenId: Boolean,
    val userAlreadyHasWalletWithThisAddress: Boolean,
) {
    fun isSuccessful() = !userAlreadyHasWalletWithThisAddress && !userHasNoWalletWithGivenId
}

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

    fun updateWallet(userAccountId: String, walletUpdateRequest: UpdateWalletRequestDto): WalletUpdateResult {
        var userAlreadyHasWalletWithThisAddress = false
        var userHasNoWalletWithGivenId = false
        if (!userBlockChainWalletRepository().existsByUserAccountIdAndId(userAccountId, walletUpdateRequest.id)) {
            userHasNoWalletWithGivenId = true
        } else {
            val walletBeforeUpdate = userBlockChainWalletRepository().findOneById(walletUpdateRequest.id)
            if (walletBeforeUpdate.walletAddress != walletUpdateRequest.walletAddress &&
                userBlockChainWalletRepository().existsByUserAccountIdAndWalletAddress(userAccountId, walletUpdateRequest.walletAddress)
            ) {
                userAlreadyHasWalletWithThisAddress = true
            } else {
                val walletToUpdate = walletBeforeUpdate.copy(
                    description = walletUpdateRequest.description,
                    currency = walletUpdateRequest.currency,
                    walletAddress = walletUpdateRequest.walletAddress
                )
                userBlockChainWalletRepository().updateWallet(walletToUpdate)
                val newBalance = ethService.getEthBalance(walletToUpdate.walletAddress)
                if (newBalance != null) {
                    userBlockChainWalletRepository().updateWallet(walletToUpdate.copy(balance = newBalance))
                }
            }
        }
        return WalletUpdateResult(
            userHasNoWalletWithGivenId = userHasNoWalletWithGivenId,
            userAlreadyHasWalletWithThisAddress = userAlreadyHasWalletWithThisAddress,
        )
    }

}
