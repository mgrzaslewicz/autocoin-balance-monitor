package autocoin.balance.app.config

import autocoin.balance.blockchain.eth.Web3EthService
import java.io.File

data class AppConfig(
    val serverPort: Int,
    val serviceName: String,
    val appDataPath: String,

    val oauth2ApiUrl: String,
    val oauth2ClientId: String,
    val oauth2ClientSecret: String,

    val exchangeMediatorApiUrl: String,
    val ethNodeUrl: String = Web3EthService.ETH_NODE_URL,

    val telegrafHostname: String,
    val metricsDestination: MetricsDestination,

    val shouldStartOwnDbContainer: Boolean,
    val jdbcUrl: String,
    val dbUsername: String,
    val dbPassword: String,
) {
    val pricesFolder: String = appDataPath + File.separator + "prices"
    val metricsFolder: String = appDataPath + File.separator + "metrics"

    override fun toString() = """
        serverPort: $serverPort
        serviceName: $serviceName
        appDataPath: $appDataPath
        oauth2ApiUrl: $oauth2ApiUrl
        oauth2ClientId: $oauth2ClientId
        oauth2ClientSecret: $oauth2ClientSecret
        exchangeMediatorApiUrl: $exchangeMediatorApiUrl
        ethNodeUrl: $ethNodeUrl
        telegrafHostname: $telegrafHostname
        metricsDestination: $metricsDestination
        shouldStartOwnDbContainer: $shouldStartOwnDbContainer
        jdbcUrl: $jdbcUrl
        dbUsername: $dbUsername
        dbPassword not empty: ${dbPassword.isNotEmpty()}
    """.trimIndent()

}
