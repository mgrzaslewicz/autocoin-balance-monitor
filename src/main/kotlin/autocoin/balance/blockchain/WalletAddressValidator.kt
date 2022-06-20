package autocoin.balance.blockchain

interface WalletAddressValidator {
    fun isWalletAddressValid(walletAddress: String): Boolean
    val currency: String
}

class MultiWalletAddressValidator(walletAddressValidators: List<WalletAddressValidator>) {
    private val walletAddressValidatorMap: Map<String, WalletAddressValidator> = walletAddressValidators.associateBy { it.currency }

    fun isWalletAddressValid(currency: String, walletAddress: String): Boolean {
        return walletAddressValidatorMap.getValue(currency).isWalletAddressValid(walletAddress)
    }

}
