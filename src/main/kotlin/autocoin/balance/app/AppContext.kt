package autocoin.balance.app

import autocoin.balance.api.ServerBuilder
import autocoin.balance.api.controller.EthWalletController
import autocoin.balance.api.controller.HealthController
import autocoin.balance.api.controller.WalletController
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.blockchain.eth.Web3EthService
import autocoin.balance.health.HealthService
import autocoin.balance.health.db.DbHealthCheck
import autocoin.balance.metrics.MetricsService
import autocoin.balance.oauth.client.AccessTokenAuthenticator
import autocoin.balance.oauth.client.AccessTokenInterceptor
import autocoin.balance.oauth.client.ClientCredentialsAccessTokenProvider
import autocoin.balance.oauth.server.AccessTokenChecker
import autocoin.balance.oauth.server.Oauth2AuthenticationMechanism
import autocoin.balance.oauth.server.Oauth2BearerTokenAuthHandlerWrapper
import autocoin.balance.scheduled.HealthMetricsScheduler
import autocoin.balance.wallet.UserBlockChainWalletRepository
import autocoin.metrics.JsonlFileStatsDClient
import com.timgroup.statsd.NonBlockingStatsDClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import mu.KLogging
import okhttp3.OkHttpClient
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

fun createJdbi(datasource: DataSource): Jdbi {
    val jdbi = Jdbi.create(datasource)
    with(jdbi) {
        installPlugin(KotlinPlugin())
        installPlugin(KotlinSqlObjectPlugin())
    }
    return jdbi
}

fun createLiquibase(datasource: DataSource): Liquibase {
    val liquibase =
        Liquibase(
            /* changeLogFile = */ "dbschema.sql",
            /* resourceAccessor = */ ClassLoaderResourceAccessor(),
            /* conn = */ JdbcConnection(datasource.connection)
        )
    return liquibase
}

fun createDatasource(jdbcUrl: String, username: String, password: String): DataSource {
    val config = HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        this.username = username
        this.password = password
        minimumIdle = 5
        maximumPoolSize = 10
        connectionTimeout = 250
        keepaliveTime = 5000
    }
    return HikariDataSource(config)
}

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

    val jdbi = AtomicReference<Jdbi>()

    private fun createDatasource() = datasource.set(
        createDatasource(
            jdbcUrl = appConfig.jdbcUrl,
            username = appConfig.dbUsername,
            password = appConfig.dbPassword,
        )
    )


    fun initDbRelatedServices() {
        createDatasource()
        createLiquibase()
        createJdbi()
    }

    private fun createJdbi() = jdbi.set(createJdbi(datasource.get()))

    private fun createLiquibase() = liquibase.set(createLiquibase(datasource.get()))

    val liquibase = AtomicReference<Liquibase>()

    val healthChecks = listOf(
        DbHealthCheck(datasource = datasource)
    )
    val healthService = HealthService(healthChecks)

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

    val walletController = WalletController(
        objectMapper = objectMapper,
        oauth2BearerTokenAuthHandlerWrapper = oauth2BearerTokenAuthHandlerWrapper,
        userBlockChainWalletRepository = { jdbi.get().onDemand(UserBlockChainWalletRepository::class.java) }
    )

    val controllers = listOf(healthController, ethWalletController, walletController)

    val server = ServerBuilder(appConfig.appServerPort, controllers, metricsService).build()


}