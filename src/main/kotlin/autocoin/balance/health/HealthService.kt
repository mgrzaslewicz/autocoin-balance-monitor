package autocoin.balance.health

data class Health(
    val healthy: Boolean,
    val unhealthyReasons: List<String>,
)

class HealthService {
    fun getHealth(): Health {
        val health = Health(
            healthy = true,
            unhealthyReasons = emptyList(),
        )
        return health
    }
}
