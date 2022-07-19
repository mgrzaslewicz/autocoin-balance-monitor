package autocoin.balance.price

import autocoin.balance.eventbus.EventType

val pricesUpdatedEventType = object : EventType<Collection<CurrencyPrice>> {
    override fun isAsync() = true
}
