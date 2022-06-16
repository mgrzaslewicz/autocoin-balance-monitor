package autocoin.balance.wallet

import autocoin.balance.api.controller.UpdateWalletRequestDto
import autocoin.balance.blockchain.eth.EthService
import mu.KLogging

data class WalletUpdateResult(
    val isAddressDuplicated: Boolean,
    val isIdInvalid: Boolean,
) {
    fun isSuccessful() = !isAddressDuplicated && !isIdInvalid
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
        var isWalletAddressDuplicated = false
        var isWalletIdInvalid = false
        if (userBlockChainWalletRepository().existsByUserAccountIdAndWalletAddress(userAccountId, walletUpdateRequest.walletAddress)) {
            isWalletAddressDuplicated = true
        } else if (!userBlockChainWalletRepository().existsByUserAccountIdAndId(userAccountId, walletUpdateRequest.id)) {
            isWalletIdInvalid = true
        } else {
            val walletToUpdate = userBlockChainWalletRepository().findOneById(walletUpdateRequest.id)
                .copy(
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
        return WalletUpdateResult(
            isAddressDuplicated = isWalletAddressDuplicated,
            isIdInvalid = isWalletIdInvalid,
        )
    }

}
