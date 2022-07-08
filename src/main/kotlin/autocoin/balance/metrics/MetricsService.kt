package autocoin.balance.metrics

import autocoin.metrics.MetricsService
import com.timgroup.statsd.StatsDClient

class MetricsService(private val statsDClient: StatsDClient) : MetricsService(statsDClient) {
    fun reportDbConnectionHealth(connectedToDb: Boolean) {
        val healthMetric = "db-connection"
        statsDClient.recordGaugeValue(healthMetric, if (connectedToDb) 1L else 0L)
    }

}
