package autocoin.balance.app

import java.io.File

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


/**
 * Use it to start own container and fill it with sample data. When set to true,
 * db connection parameters will be set as required JVM properties
 */
val shouldStartOwnDbContainer: Boolean by lazy { getPropertyThenEnv("START_OWN_DB_CONTAINER", "false").toBoolean() }

private const val JDBC_URL_PARAMETER = "JDBC_URL"
private const val DB_USERNAME_PARAMETER = "DB_USERNAME"
private const val DB_PASSWORD_PARAMETER = "DB_PASSWORD"

data class AppConfig(
    val appServerPort: Int = getPropertyThenEnv("APP_SERVER_PORT", "10022").toInt(),
    val serviceName: String = getPropertyThenEnv("SERVICE_NAME"),

    @InternalDependency
    val oauth2ApiBaseUrl: String = getPropertyThenEnv("OAUTH2_API_URL", "https://users-apiv2.autocoin-trader.com"),

    @InternalDependency
    val oauth2ClientId: String = getPropertyThenEnv("OAUTH_CLIENT_ID", "balance-monitor"),
    @InternalDependency
    val oauth2ClientSecret: String = getPropertyThenEnv("OAUTH_CLIENT_SECRET"),

    val metricsFolder: String = getPropertyThenEnv("APP_DATA_PATH", "data") + File.separator + "metrics",

    @ExternalDependency
    val ethNodeUrl: String = getPropertyThenEnv("ETH_NODE_URL", "https://main-light.eth.linkpool.io"),

    @InternalDependency
    val useMetrics: Boolean = getPropertyThenEnv("USE_METRICS", "true").toBoolean(),

    @InternalDependency
    val telegrafHostname: String = getPropertyThenEnv("TELEGRAF_HOSTNAME", "telegraf"),

    @InternalDependency
    val jdbcUrl: String = getPropertyThenEnv(JDBC_URL_PARAMETER),
    @InternalDependency
    val dbUsername: String = getPropertyThenEnv(DB_USERNAME_PARAMETER),
    @InternalDependency
    val dbPassword: String = getPropertyThenEnv(DB_PASSWORD_PARAMETER),
) {
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
    return System.getProperty(propertyName, System.getenv(propertyName))
}

private fun <T> getPropertyThenEnv(propertyName: String, existingPropertyParser: (String) -> T, defaultValue: T): T {
    val propertyValue = System.getProperty(propertyName, System.getenv(propertyName))
    return if (propertyValue != null) {
        existingPropertyParser(propertyValue)
    } else {
        defaultValue
    }
}

private fun getPropertyThenEnv(propertyName: String, defaultValue: String): String {
    return System.getProperty(propertyName, System.getenv(propertyName).orElse(defaultValue))
}

private fun String?.orElse(value: String) = this ?: value
