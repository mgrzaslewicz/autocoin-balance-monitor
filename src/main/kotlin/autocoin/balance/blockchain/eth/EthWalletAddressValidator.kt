package autocoin.balance.blockchain.eth

import autocoin.balance.blockchain.WalletAddressValidator

class EthWalletAddressValidator : WalletAddressValidator {
    private val ethWalletAddressRegex = Regex("^0x[a-fA-F0-9]{40}\$")

    override fun isWalletAddressValid(walletAddress: String) = ethWalletAddressRegex.matches(walletAddress)
    override val currency: String = "ETH"

}
