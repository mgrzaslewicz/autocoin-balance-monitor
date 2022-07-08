package autocoin.balance.blockchain.btc

import autocoin.balance.blockchain.WalletAddressValidator

class BtcWalletAddressValidator : WalletAddressValidator {
    private val btcWalletAddressRegex = Regex("[13]{1}[a-km-zA-HJ-NP-Z1-9]{26,33}|bc1[a-z0-9]{39,59}")

    override fun isWalletAddressValid(walletAddress: String) = btcWalletAddressRegex.matches(walletAddress)

    override val currency = "BTC"
}
