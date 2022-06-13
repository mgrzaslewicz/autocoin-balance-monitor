package autocoin.balance.app

import autocoin.balance.api.ServerBuilder
import autocoin.balance.api.controller.EthWalletController
import autocoin.balance.api.controller.HealthController
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.blockchain.eth.Web3EthService
import autocoin.balance.health.HealthService
import autocoin.balance.metrics.MetricsService
import autocoin.balance.oauth.client.AccessTokenAuthenticator
import autocoin.balance.oauth.client.AccessTokenInterceptor
import autocoin.balance.oauth.client.ClientCredentialsAccessTokenProvider
import autocoin.balance.oauth.server.AccessTokenChecker
import autocoin.balance.oauth.server.Oauth2AuthenticationMechanism
import autocoin.balance.oauth.server.Oauth2BearerTokenAuthHandlerWrapper
import autocoin.balance.scheduled.HealthMetricsScheduler
import autocoin.metrics.JsonlFileStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import mu.KLogging
import okhttp3.OkHttpClient
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

class AppContext(private val appConfig: AppConfig) {
    private companion object : KLogging()

    val httpClientWithoutAuthorization = OkHttpClient()
    val objectMapper = ObjectMapperProvider().createObjectMapper()
    val accessTokenProvider = ClientCredentialsAccessTokenProvider(
        httpClient = httpClientWithoutAuthorization,
        objectMapper = objectMapper,
        oauth2ServerUrl = appConfig.oauth2ApiBaseUrl,
        oauthClientId = appConfig.oauth2ClientId,
        oauthClientSecret = appConfig.oauth2ClientSecret
    )
    val accessTokenAuthenticator = AccessTokenAuthenticator(accessTokenProvider)
    val accessTokenInterceptor = AccessTokenInterceptor(accessTokenProvider)
    val oauth2HttpClient = OkHttpClient.Builder()
        .authenticator(accessTokenAuthenticator)
        .addInterceptor(accessTokenInterceptor)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    val statsdClient = if (appConfig.useMetrics) {
        NonBlockingStatsDClient(appConfig.serviceName, appConfig.telegrafHostname, 8125)
    } else {
        val metricsFolderPath = Path.of(appConfig.metricsFolder)
        metricsFolderPath.toFile().mkdirs()
        val metricsFile = metricsFolderPath.resolve("metrics.jsonl")
        logger.warn { "Using JsonlFileStatsDClient, telegraf.hostname not provided. Writing metrics to ${metricsFile.toAbsolutePath()}" }
        JsonlFileStatsDClient(metricsFile.toFile())
    }
    val metricsService: MetricsService = MetricsService(statsdClient)


    val scheduledJobsxecutorService = Executors.newScheduledThreadPool(3)


    /**
     * lazy because object creation (AppContext instance) should have no side effects
     * and getConnection actually makes a connection to DB
     */
    val datasource = AtomicReference<DataSource>()

    fun createDatasource() {
        val config = HikariConfig().apply {
            jdbcUrl = appConfig.jdbcUrl
            username = appConfig.dbUsername
            password = appConfig.dbPassword
            minimumIdle = 5
            maximumPoolSize = 10
            connectionTimeout = 250
            keepaliveTime = 5000
        }
        datasource.set(HikariDataSource(config))
    }

    val liquibase: Liquibase by lazy { Liquibase("dbschema.sql", ClassLoaderResourceAccessor(), JdbcConnection(datasource.get().connection)) }

    val healthService = HealthService(datasource = datasource)

    val healthMetricsScheduler = HealthMetricsScheduler(
        healthService = healthService,
        metricsService = metricsService,
        executorService = scheduledJobsxecutorService
    )

    val accessTokenChecker = AccessTokenChecker(httpClientWithoutAuthorization, objectMapper, appConfig)
    val oauth2AuthenticationMechanism = Oauth2AuthenticationMechanism(accessTokenChecker)
    val oauth2BearerTokenAuthHandlerWrapper = Oauth2BearerTokenAuthHandlerWrapper(oauth2AuthenticationMechanism)

    val ethService = Web3EthService(ethNodeUrl = appConfig.ethNodeUrl)

    val ethWalletAddressValidator = EthWalletAddressValidator()

    val ethWalletController = EthWalletController(
        objectMapper = objectMapper,
        ethService = ethService,
        oauth2BearerTokenAuthHandlerWrapper = oauth2BearerTokenAuthHandlerWrapper,
        ethWalletAddressValidator = ethWalletAddressValidator,
    )

    val healthController = HealthController(
        healthService = healthService,
        objectMapper = objectMapper,
    )

    val controllers = listOf(healthController, ethWalletController)

    val server = ServerBuilder(appConfig.appServerPort, controllers, metricsService).build()


}
