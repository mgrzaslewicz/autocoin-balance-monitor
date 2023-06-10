package autocoin.balance.health.blockchainwallet

import autocoin.balance.blockchain.eth.EthService
import autocoin.balance.health.HealthCheck
import autocoin.balance.health.HealthCheckResult
import mu.KLogging
import java.math.BigDecimal

class EthServiceHealthCheck(private val ethService: EthService) : HealthCheck {
    private companion object : KLogging()

    /**
     * First wallet taken from https://mycrypto.tools/sample_ethaddresses.html
     */
    private val sampleEthWalletAddress = "0xDBC05B1ECB4FDAEF943819C0B04E9EF6DF4BABD6"

    override fun doHealthCheck(): HealthCheckResult {
        val ethBalance: BigDecimal? = ethService.getEthBalance(sampleEthWalletAddress)
        return HealthCheckResult(
            description = "Ethereum wallet balance retrieval",
            healthy = ethBalance != null && BigDecimal.ZERO.compareTo(ethBalance) == 0,
            details = mapOf(
                "sample wallet $sampleEthWalletAddress balance" to "$ethBalance",
            ),
            healthCheckClass = this.javaClass,
            unhealthyReasons = listOfNotNull(
                if (ethBalance == null) "Could not get wallet $sampleEthWalletAddress balance. Check logs for details" else null,
            ),
        )
    }
}
