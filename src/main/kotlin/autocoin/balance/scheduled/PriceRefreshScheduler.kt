package autocoin.balance.scheduled

import autocoin.balance.price.CachingPriceService
import autocoin.balance.price.CurrencyRepository
import mu.KLogging
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PriceRefreshScheduler(
    private val refreshPricesInterval: Duration = Duration.of(1, ChronoUnit.HOURS),
    private val currencyRepository: () -> CurrencyRepository,
    private val cachingPriceService: CachingPriceService,
    private val executorService: ScheduledExecutorService,
) {
    private companion object : KLogging()

    private var firstTime = true

    fun scheduleRefreshingPrices() {
        logger.info { "Scheduling refreshing prices existing in wallets every $refreshPricesInterval" }
        executorService.scheduleAtFixedRate({
            try {
                if (firstTime) {
                    logger.info { "Refreshing prices existing in wallets every $refreshPricesInterval for the first time" }
                    firstTime = false
                }
                val uniqueCurrencies = currencyRepository().selectUniqueWalletCurrencies()
                cachingPriceService.refreshCurrencyPrices(uniqueCurrencies)
            } catch (e: Exception) {
                logger.error(e) { "Could not refresh prices" }
            }
        }, 0, refreshPricesInterval.seconds, TimeUnit.SECONDS)
    }

}
