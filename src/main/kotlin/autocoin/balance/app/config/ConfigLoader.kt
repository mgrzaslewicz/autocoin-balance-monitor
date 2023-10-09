package autocoin.balance.app.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import mu.KotlinLogging

object ConfigLoader {
    private val logger = KotlinLogging.logger {}

    private fun getProfileConfig(profile: String): Config = ConfigFactory.parseResources("config/app-$profile.conf")

    fun loadConfig(profiles: String? = System.getProperty("PROFILES", System.getenv("PROFILES"))): AppConfig {
        val profiles = profiles?.split(",")
        logger.info { "Loading config for profiles: $profiles" }

        val defaultConfig = ConfigFactory.load("config/app-base")
        val config = ConfigFactory
            .systemEnvironment()
            .withFallback(ConfigFactory.systemProperties())
            .let {
                var currentConfig = it
                profiles?.forEach { profile ->
                    currentConfig = currentConfig.withFallback(getProfileConfig(profile))
                }
                currentConfig
            }
            .withFallback(defaultConfig)
            .resolve()

        return AppConfig(
            serverPort = config.getInt("server.port"),
            serviceName = config.getString("service.name"),

            oauth2ApiUrl = config.getString("externalServices.oauth.apiUrl"),
            oauth2ClientId = config.getString("externalServices.oauth.clientId"),
            oauth2ClientSecret = config.getString("externalServices.oauth.clientSecret"),

            exchangeMediatorApiUrl = config.getString("externalServices.exchangeMediator.apiUrl"),
            telegrafHostname = config.getString("externalServices.telegrafHostname"),

            appDataPath = config.getString("service.dataFolder"),

            metricsDestination = MetricsDestination.valueOf(config.getString("metrics.destination")),

            shouldStartOwnDbContainer = config.getBoolean("db.startOwnDockerContainer"),
            jdbcUrl = config.getString("db.jdbcUrl"),
            dbUsername = config.getString("db.username"),
            dbPassword = config.getString("db.password"),
        )
    }
}
