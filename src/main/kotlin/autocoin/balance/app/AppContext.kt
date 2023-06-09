package autocoin.balance.app

import autocoin.balance.api.ServerBuilder
import autocoin.balance.api.controller.*
import autocoin.balance.blockchain.BlockChainExplorerUrlService
import autocoin.balance.blockchain.MultiBlockchainWalletService
import autocoin.balance.blockchain.MultiWalletAddressValidator
import autocoin.balance.blockchain.btc.BtcService
import autocoin.balance.blockchain.btc.BtcWalletAddressValidator
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import autocoin.balance.blockchain.eth.Web3EthService
import autocoin.balance.eventbus.DefaultEventBus
import autocoin.balance.health.HealthService
import autocoin.balance.health.db.DbHealthCheck
import autocoin.balance.metrics.MetricsService
import autocoin.balance.oauth.client.AccessTokenAuthenticator
import autocoin.balance.oauth.client.AccessTokenInterceptor
import autocoin.balance.oauth.client.ClientCredentialsAccessTokenProvider
import autocoin.balance.oauth.server.AccessTokenChecker
import autocoin.balance.oauth.server.Oauth2AuthenticationMechanism
import autocoin.balance.oauth.server.Oauth2BearerTokenAuthHandlerWrapper
import autocoin.balance.price.CachingPriceService
import autocoin.balance.price.CurrencyRepository
import autocoin.balance.price.RestPriceService
import autocoin.balance.price.pricesUpdatedEventType
import autocoin.balance.price.repository.FilePriceRepository
import autocoin.balance.scheduled.HealthMetricsScheduler
import autocoin.balance.scheduled.PriceRefreshScheduler
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.blockchain.UserBlockChainWalletService
import autocoin.balance.wallet.currency.UserCurrencyAssetRepository
import autocoin.balance.wallet.currency.UserCurrencyAssetService
import autocoin.balance.wallet.exchange.RestExchangeMediatorWalletService
import autocoin.balance.wallet.exchange.UserExchangeWalletLastRefreshRepository
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWalletService
import autocoin.balance.wallet.summary.DefaultUserBalanceSummaryService
import autocoin.balance.wallet.summary.UserBalanceSummaryRepository
import autocoin.metrics.JsonlFileStatsDClient
import automate.profit.autocoin.exchange.time.SystemTimeMillisProvider
import automate.profit.autocoin.keyvalue.FileKeyValueRepository
import com.timgroup.statsd.NonBlockingStatsDClient
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KLogging
import okhttp3.OkHttpClient
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.kotlin.KotlinSqlObjectPlugin
import java.nio.file.Path
import java.nio.file.Paths
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

fun createDbMigrator(datasource: DataSource) = LiquibaseDbMigrator(datasource)

fun createDatasource(jdbcUrl: String, username: String, password: String): HikariDataSource {
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

    val statsdClient = if (appConfig.metricsDestination == MetricsDestination.TELEGRAF) {
        NonBlockingStatsDClient(appConfig.serviceName, appConfig.telegrafHostname, 8125)
    } else {
        val metricsFolderPath = Path.of(appConfig.metricsFolder)
        metricsFolderPath.toFile().mkdirs()
        val metricsFile = metricsFolderPath.resolve("metrics.jsonl")
        logger.warn { "Using JsonlFileStatsDClient as metricsDestination set to 'FILE'. Writing metrics to ${metricsFile.toAbsolutePath()}" }
        JsonlFileStatsDClient(metricsFile.toFile())
    }

    val metricsService: MetricsService = MetricsService(statsdClient)

    val scheduledJobsxecutorService = Executors.newScheduledThreadPool(3)


    /**
     * lazy because object creation (AppContext instance) should have no side effects
     * and getConnection actually makes a connection to DB
     */
    val datasource = AtomicReference<HikariDataSource>()

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
        createJdbi()
    }

    private fun createJdbi() = jdbi.set(createJdbi(datasource.get()))


    val healthChecks = listOf(
        DbHealthCheck(datasource = datasource)
    )
    val healthService = HealthService(healthChecks, AppVersion().commitId)

    val healthMetricsScheduler = HealthMetricsScheduler(
        healthService = healthService,
        metricsService = metricsService,
        executorService = scheduledJobsxecutorService
    )

    val accessTokenChecker = AccessTokenChecker(httpClientWithoutAuthorization, objectMapper, appConfig)
    val oauth2AuthenticationMechanism = Oauth2AuthenticationMechanism(accessTokenChecker)
    val oauth2BearerTokenAuthHandlerWrapper = Oauth2BearerTokenAuthHandlerWrapper(oauth2AuthenticationMechanism)

    val ethService = Web3EthService(ethNodeUrl = appConfig.ethNodeUrl)
    val btcService = BtcService(httpClient = httpClientWithoutAuthorization)

    val ethWalletAddressValidator = EthWalletAddressValidator()
    val btcWalletAddressValidator = BtcWalletAddressValidator()

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

    val multiBlockchainWalletService = MultiBlockchainWalletService(
        blockchainWalletServices = listOf(
            btcService,
            ethService,
        )
    )

    val multiWalletAddressValidator = MultiWalletAddressValidator(
        walletAddressValidators = listOf(
            btcWalletAddressValidator,
            ethWalletAddressValidator,
        )
    )

    val userBlockChainWalletService = UserBlockChainWalletService(
        userBlockChainWalletRepository = { jdbi.get().onDemand(UserBlockChainWalletRepository::class.java) },
        multiBlockchainWalletService = multiBlockchainWalletService,
    )

    private val timeMillisProvider = SystemTimeMillisProvider()

    val eventBus = DefaultEventBus(executorService = scheduledJobsxecutorService)

    val fileKeyValueRepository = FileKeyValueRepository(timeMillisProvider = timeMillisProvider)

    val filePriceRepository = FilePriceRepository(
        fileKeyValueRepository = fileKeyValueRepository,
        objectMapper = objectMapper,
        pricesFolder = Paths.get(appConfig.pricesFolder),
    )

    val priceService = CachingPriceService(
        decorated = RestPriceService(
            priceApiUrl = appConfig.exchangeMediatorApiBaseUrl,
            httpClient = oauth2HttpClient,
            metricsService = metricsService,
            objectMapper = objectMapper
        ),
        eventBus = eventBus.apply {
            this.register(pricesUpdatedEventType, filePriceRepository::savePrices)
        },
    )

    val priceRefreshScheduler = PriceRefreshScheduler(
        currencyRepository = { jdbi.get().onDemand(CurrencyRepository::class.java) },
        executorService = scheduledJobsxecutorService,
        cachingPriceService = priceService,
    )

    val exchangeMediatorWalletService = RestExchangeMediatorWalletService(
        objectMapper = objectMapper,
        // it might be a long operation to get all wallets from many exchanges
        httpClient = oauth2HttpClient.newBuilder().callTimeout(1, TimeUnit.MINUTES).build(),
        exchangeMediatorApiUrl = appConfig.exchangeMediatorApiBaseUrl,
    )

    val userExchangeWalletService = UserExchangeWalletService(
        exchangeMediatorWalletService = exchangeMediatorWalletService,
        userExchangeWalletRepository = { jdbi.get().onDemand(UserExchangeWalletRepository::class.java) },
        userExchangeWalletLastRefreshRepository = {
            jdbi.get().onDemand(UserExchangeWalletLastRefreshRepository::class.java)
        },
        priceService = priceService,
        timeMillisProvider = timeMillisProvider,
    )


    val exchangeWalletController = ExchangeWalletController(
        objectMapper = objectMapper,
        oauth2BearerTokenAuthHandlerWrapper = oauth2BearerTokenAuthHandlerWrapper,
        userExchangeWalletRepository = { jdbi.get().onDemand(UserExchangeWalletRepository::class.java) },
        userExchangeWalletService = userExchangeWalletService,
        priceService = priceService,
        timeMillisProvider = timeMillisProvider,
    )

    val blockChainExplorerUrlService = BlockChainExplorerUrlService()

    val blockchainWalletController = BlockchainWalletController(
        objectMapper = objectMapper,
        oauth2BearerTokenAuthHandlerWrapper = oauth2BearerTokenAuthHandlerWrapper,
        userBlockChainWalletRepository = { jdbi.get().onDemand(UserBlockChainWalletRepository::class.java) },
        walletAddressValidator = multiWalletAddressValidator,
        userBlockChainWalletService = userBlockChainWalletService,
        priceService = priceService,
        blockChainExplorerUrlService = blockChainExplorerUrlService,
    )

    val userCurrencyAssetService = UserCurrencyAssetService(
        currencyAssetRepository = { jdbi.get().onDemand(UserCurrencyAssetRepository::class.java) },
        priceService = priceService,
    )

    val userBalanceSummaryService = DefaultUserBalanceSummaryService(
        priceService = priceService,
        userBalanceSummaryRepository = { jdbi.get().onDemand(UserBalanceSummaryRepository::class.java) },
        userExchangeWalletRepository = { jdbi.get().onDemand(UserExchangeWalletRepository::class.java) },
        userBlockChainWalletRepository = { jdbi.get().onDemand(UserBlockChainWalletRepository::class.java) },
        userCurrencyAssetService = userCurrencyAssetService,
        executorService = Executors.newCachedThreadPool(),
        userExchangeWalletService = userExchangeWalletService,
        userBlockChainWalletService = userBlockChainWalletService,
    )

    val balanceSummaryController = BalanceSummaryController(
        objectMapper = objectMapper,
        oauth2BearerTokenAuthHandlerWrapper = oauth2BearerTokenAuthHandlerWrapper,
        userBalanceSummaryService = userBalanceSummaryService,
        priceService = priceService,
    )


    val userCurrencyAssetController = UserCurrencyAssetController(
        objectMapper = objectMapper,
        oauth2BearerTokenAuthHandlerWrapper = oauth2BearerTokenAuthHandlerWrapper,
        userCurrencyAssetService = userCurrencyAssetService,
        userCurrencyAssetRepository = { jdbi.get().onDemand(UserCurrencyAssetRepository::class.java) },
        blockChainExplorerUrlService = blockChainExplorerUrlService,
        priceService = priceService,
    )

    val controllers = listOf(
        healthController,
        ethWalletController,
        blockchainWalletController,
        exchangeWalletController,
        balanceSummaryController,
        userCurrencyAssetController,
    )

    val server = ServerBuilder(appConfig.appServerPort, controllers, metricsService).build()

}
