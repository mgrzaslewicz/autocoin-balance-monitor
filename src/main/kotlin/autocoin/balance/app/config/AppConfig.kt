package autocoin.balance.app.config

import autocoin.balance.blockchain.eth.Web3EthService
import java.io.File

/**
 * Use it to start own container and fill it with sample data. When set to true,
 * db connection parameters will be set as required JVM properties
 */
val shouldStartOwnDbContainer: Boolean by lazy { getPropertyThenEnv("START_OWN_DB_CONTAINER", "false").toBoolean() }

private const val JDBC_URL_PARAMETER = "JDBC_URL"
private const val DB_USERNAME_PARAMETER = "DB_USERNAME"
private const val DB_PASSWORD_PARAMETER = "DB_PASSWORD"


enum class MetricsDestination {
    FILE,
    TELEGRAF,
}

data class AppConfig(
    val serverPort: Int = getPropertyThenEnv("APP_SERVER_PORT", "10022").toInt(),
    val serviceName: String = getPropertyThenEnv("SERVICE_NAME", "autocoin-balance-monitor"),
    val appDataPath: String = getPropertyThenEnv("APP_DATA_PATH", "data"),


    val oauth2ApiUrl: String = getPropertyThenEnv("OAUTH2_API_URL", "http://autocoin-auth-service:9002"),
    val oauth2ClientId: String = serviceName,
    val oauth2ClientSecret: String = getPropertyThenEnv("OAUTH_CLIENT_SECRET"),

    val exchangeMediatorApiUrl: String = getPropertyThenEnv(
        "EXCHANGE_MEDIATOR_API_URL",
        "http://autocoin-exchange-mediator:9001"
    ),

    val ethNodeUrl: String = getPropertyThenEnv("ETH_NODE_URL", Web3EthService.ETH_NODE_URL),


    val telegrafHostname: String = getPropertyThenEnv("TELEGRAF_HOSTNAME", "telegraf"),
    val metricsDestination: MetricsDestination = MetricsDestination.valueOf(
        getPropertyThenEnv(
            "METRICS_DESTINATION",
            MetricsDestination.FILE.name
        )
    ),

    val jdbcUrl: String = getPropertyThenEnv(JDBC_URL_PARAMETER),
    val dbUsername: String = getPropertyThenEnv(DB_USERNAME_PARAMETER),
    val dbPassword: String = getPropertyThenEnv(DB_PASSWORD_PARAMETER),
) {
    val pricesFolder: String = appDataPath + File.separator + "prices"
    val metricsFolder: String = appDataPath + File.separator + "metrics"

    companion object {
        fun setJvmPropertiesForDbConnection(jdbcUrl: String, dbUsername: String, dbPassword: String) {
            System.setProperty(JDBC_URL_PARAMETER, jdbcUrl)
            System.setProperty(DB_USERNAME_PARAMETER, dbUsername)
            System.setProperty(DB_PASSWORD_PARAMETER, dbPassword)
        }
    }
}

fun loadConfig(): AppConfig {
    return AppConfig()
}

private fun getPropertyThenEnv(propertyName: String): String {
    try {
        return System.getProperty(propertyName, System.getenv(propertyName))
    } catch (e: Exception) {
        throw IllegalStateException("Can't getPropertyThenEnv $propertyName", e)
    }
}

private fun getPropertyThenEnv(propertyName: String, defaultValue: String): String {
    return System.getProperty(propertyName, System.getenv(propertyName).orElse(defaultValue))
}

private fun String?.orElse(value: String) = this ?: value
