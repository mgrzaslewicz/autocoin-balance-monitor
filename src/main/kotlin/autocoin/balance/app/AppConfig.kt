package autocoin.balance.app

import java.io.File
import java.lang.System.getProperty
import java.lang.System.getenv

/**
 * Informational marker that configuration is related to some service within autocoin infrastructure that you can control
 */
@Retention(AnnotationRetention.SOURCE)
annotation class InternalDependency

/**
 * Informational marker that configuration is related to some external service that you don't have any control of
 */
@Retention(AnnotationRetention.SOURCE)
annotation class ExternalDependency

data class AppConfig(

    val appServerPort: Int = getPropertyThenEnv("APP_SERVER_PORT", "10022").toInt(),
    val serviceName: String = getPropertyThenEnv("SERVICE_NAME"),

    @InternalDependency
    val oauth2ApiBaseUrl: String = getPropertyThenEnv("OAUTH2_API_URL", "https://users-apiv2.autocoin-trader.com"),

    @InternalDependency
    val oauth2ClientId: String = getPropertyThenEnv("OAUTH_CLIENT_ID", "wallet-service"),
    @InternalDependency
    val oauth2ClientSecret: String = getPropertyThenEnv("OAUTH_CLIENT_SECRET"),

    val metricsFolder: String = getPropertyThenEnv("APP_DATA_PATH", "data") + File.separator + "metrics",

    @ExternalDependency
    val ethNodeUrl: String = getPropertyThenEnv("ETH_NODE_URL", "https://main-light.eth.linkpool.io"),

    @InternalDependency
    val useMetrics: Boolean = getPropertyThenEnv("USE_METRICS", "true").toBoolean(),

    @InternalDependency
    val telegrafHostname: String = getPropertyThenEnv("TELEGRAF_HOSTNAME", "telegraf")
)

fun loadConfig(): AppConfig {
    return AppConfig()
}

private fun getPropertyThenEnv(propertyName: String): String {
    return getProperty(propertyName, getenv(propertyName))
}

private fun <T> getPropertyThenEnv(propertyName: String, existingPropertyParser: (String) -> T, defaultValue: T): T {
    val propertyValue = getProperty(propertyName, getenv(propertyName))
    return if (propertyValue != null) {
        existingPropertyParser(propertyValue)
    } else {
        defaultValue
    }
}

private fun getPropertyThenEnv(propertyName: String, defaultValue: String): String {
    return getProperty(propertyName, getenv(propertyName).orElse(defaultValue))
}

private fun String?.orElse(value: String) = this ?: value
