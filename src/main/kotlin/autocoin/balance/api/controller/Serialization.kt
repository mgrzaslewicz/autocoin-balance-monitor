package autocoin.balance.api.controller

import java.math.BigDecimal

fun Map<String, BigDecimal?>.toStringValueMap() = this.map { entry -> entry.key to entry.value?.toPlainString() }.toMap()
