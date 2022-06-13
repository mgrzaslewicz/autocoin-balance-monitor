package autocoin.balance.health

import mu.KLogging

data class Health(
    val healthy: Boolean,
    val healthChecks: List<HealthCheckResult>,
) {
    fun findHealthCheckResult(healthCheckClass: Class<out HealthCheck>): HealthCheckResult {
        return healthChecks.first { it.healthCheckClass == healthCheckClass }
    }
}

data class HealthCheckResult(
    val description: String,
    val healthy: Boolean,
    val healthCheckClass: Class<HealthCheck>,
    val unhealthyReasons: List<String>,
)

interface HealthCheck {
    fun doHealthCheck(): HealthCheckResult
}

class HealthService(private val healthChecks: List<HealthCheck>) {

    private companion object : KLogging()

    fun getHealth(): Health {
        val healthCheckResults = healthChecks.map { it.doHealthCheck() }
        val health = Health(
            healthy = healthCheckResults.all { it.healthy },
            healthChecks = healthCheckResults,
        )
        return health
    }
}
