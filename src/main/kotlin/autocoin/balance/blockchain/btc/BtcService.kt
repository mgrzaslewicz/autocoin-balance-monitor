package autocoin.balance.blockchain.btc

import autocoin.balance.blockchain.BlockchainWalletService
import mu.KLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal

class BtcService(private val httpClient: OkHttpClient) : BlockchainWalletService {
    private companion object : KLogging()

    override fun getBalance(walletAddress: String): BigDecimal? {
        val request = Request.Builder()
            .url("https://blockchain.info/q/addressbalance/$walletAddress")
            .get()
            .build()
        try {
            val response = httpClient.newCall(request).execute()
            response.use {
                val satoshiString = response.body?.string()
                logger.info { "Balance of address $walletAddress is $satoshiString satoshi" }
                val btcBalance = BigDecimal(satoshiString).movePointLeft(8)
                return btcBalance
            }
        } catch (e: Exception) {
            logger.error(e) { "Could not get wallet $walletAddress balance" }
            return null
        }
    }

    override val currency = "BTC"
}
