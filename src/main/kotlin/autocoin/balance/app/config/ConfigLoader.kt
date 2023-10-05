package autocoin.balance.app.config

import com.typesafe.config.ConfigFactory

object ConfigLoader {

    fun loadConfig(profile: String? = System.getProperty("PROFILE", System.getenv("PROFILE"))): AppConfig {
        val defaultConfig = ConfigFactory.load("config/app-base")
        val profileConfig = if (profile == null) {
            ConfigFactory.empty()
        } else {
            ConfigFactory.parseResources("config/app-$profile.conf")
        }
        val config = ConfigFactory
            .systemEnvironment()
            .withFallback(ConfigFactory.systemProperties())
            .withFallback(profileConfig)
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
