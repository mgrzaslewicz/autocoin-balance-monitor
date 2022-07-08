package autocoin.balance.wallet.blockchain

import autocoin.balance.api.controller.UpdateWalletRequestDto
import autocoin.balance.blockchain.MultiBlockchainWalletService
import mu.KLogging

data class WalletUpdateResult(
    val userHasNoWalletWithGivenId: Boolean,
    val userAlreadyHasWalletWithThisAddress: Boolean,
) {
    fun isSuccessful() = !userAlreadyHasWalletWithThisAddress && !userHasNoWalletWithGivenId
}

data class WalletAddResult(
    val userAlreadyHasWalletWithThisAddress: Boolean,
)

class UserBlockChainWalletService(
    private val userBlockChainWalletRepository: () -> UserBlockChainWalletRepository,
    private val multiBlockchainWalletService: MultiBlockchainWalletService
) {

    companion object : KLogging()

    fun refreshWalletBalances(userAccountId: String) {
        val userWallets = userBlockChainWalletRepository().findManyByUserAccountId(userAccountId)
        userWallets.forEach { wallet ->
            val newBalance = multiBlockchainWalletService.getBalance(wallet.currency, wallet.walletAddress)
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
                val newBalance = multiBlockchainWalletService.getBalance(walletToUpdate.currency, walletToUpdate.walletAddress)
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

    fun addWallet(userAccountId: String, walletToAdd: UserBlockChainWallet): WalletAddResult {
        return if (userBlockChainWalletRepository().existsByUserAccountIdAndWalletAddress(userAccountId, walletToAdd.walletAddress)) {
            WalletAddResult(userAlreadyHasWalletWithThisAddress = true)
        } else {
            userBlockChainWalletRepository().insertWallet(walletToAdd)
            val newBalance = multiBlockchainWalletService.getBalance(walletToAdd.currency, walletToAdd.walletAddress)
            if (newBalance != null) {
                userBlockChainWalletRepository().updateWallet(walletToAdd.copy(balance = newBalance))
            }
            WalletAddResult(userAlreadyHasWalletWithThisAddress = false)
        }
    }

}
