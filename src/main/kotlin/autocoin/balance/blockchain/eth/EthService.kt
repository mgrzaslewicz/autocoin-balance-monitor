package autocoin.balance.blockchain.eth

import mu.KLogging
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.math.BigInteger

interface EthService {
    fun getWeiBalance(ethWalletAddress: String): BigInteger?
    fun getEthBalance(ethWalletAddress: String): BigDecimal?
}

class Web3EthService(private val ethNodeUrl: String) : EthService {
    private val web3j = Web3j.build(HttpService(ethNodeUrl))

    private companion object : KLogging()

    override fun getWeiBalance(ethWalletAddress: String): BigInteger? {
        logger.info { "Requesting balance of address $ethWalletAddress" }
        return try {
            val balanceResponse = web3j.ethGetBalance(ethWalletAddress, DefaultBlockParameter.valueOf("latest"))
                .send()
            logger.info { "Balance of address $ethWalletAddress is ${balanceResponse.balance} wei" }
            balanceResponse.balance
        } catch (e: Exception) {
            logger.error(e) { "Could not get wallet $ethWalletAddress balance" }
            null
        }
    }

    override fun getEthBalance(ethWalletAddress: String): BigDecimal? {
        val weiBalance = getWeiBalance(ethWalletAddress)?.toBigDecimal()
        return if (weiBalance != null) {
            Convert.fromWei(weiBalance, Convert.Unit.ETHER)
        } else {
            null
        }
    }

}
