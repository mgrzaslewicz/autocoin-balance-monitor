package autocoin.balance.metrics

import autocoin.metrics.MetricsService
import com.timgroup.statsd.StatsDClient

class MetricsService(private val statsDClient: StatsDClient) : MetricsService(statsDClient) {

}
