package autocoin.balance.blockchain.eth

class EthWalletAddressValidator {
    private val ethWalletAddressRegex = Regex("^0x[a-fA-F0-9]{40}\$")

    fun isWalletAddressValid(ethWalletAddress: String) = ethWalletAddressRegex.matches(ethWalletAddress)
}
