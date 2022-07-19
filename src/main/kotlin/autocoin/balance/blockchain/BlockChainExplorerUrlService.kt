package autocoin.balance.blockchain

import autocoin.balance.wallet.blockchain.UserBlockChainWallet

class BlockChainExplorerUrlService {
    fun getBlockchainExplorerUrl(wallet: UserBlockChainWallet) = getBlockchainExplorerUrl(wallet.currency, wallet.walletAddress)

    fun getBlockchainExplorerUrl(currency: String, walletAddress: String): String? {
        return when (currency) {
            "BTC" -> "https://blockchain.info/address/$walletAddress"
            "LTC" -> "https://live.blockcypher.com/ltc/address/$walletAddress"
            "DOGE" -> "https://dogechain.info/address/$walletAddress"
            "BCH" -> "https://blockchair.com/bitcoin-cash/address/$walletAddress"
            "ETH" -> "https://etherscan.io/address/$walletAddress"
            "XRP" -> "https://xrpcharts.ripple.com/address/$walletAddress"
            "ZEC" -> "https://explorer.zcha.in/address/$walletAddress"
            "XMR" -> "https://moneroblocks.info/address/$walletAddress"
            "DASH" -> "https://explorer.dash.org/address/$walletAddress"
            "EOS" -> "https://eosweb.net/account/$walletAddress"
            "BCN" -> "https://explorer.bcn.cash/account/$walletAddress"
            "XLM" -> "https://xlmcharts.com/account/$walletAddress"
            "TRX" -> "https://trx.network/address/$walletAddress"
            "NEO" -> "https://neoexplorer.io/address/$walletAddress"
            "ONT" -> "https://explorer.ont.io/address/$walletAddress"
            "ZIL" -> "https://explorer.zilliqa.com/address/$walletAddress"
            "KCS" -> "https://explorer.kcs.io/address/$walletAddress"
            "ZRX" -> "https://etherscan.io/address/$walletAddress"
            "BTS" -> "https://bts.bitnet.io/address/$walletAddress"
            "DGB" -> "https://digiexplorer.info/address/$walletAddress"
            "XVG" -> "https://explorer.xvg.io/address/$walletAddress"
            else -> null
        }
    }
}
