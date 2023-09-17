package autocoin.balance.eventbus

import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

interface EventBus {
    fun <T> register(eventType: Class<T>, eventHandler: (event: T) -> Unit, async: Boolean = false)
    fun <T> publish(eventType: Class<T>, event: T)
}

class DefaultEventBus(private val executorService: ExecutorService) : EventBus {
    private data class EventHandler(
        val handler: (payload: Any) -> Unit,
        val async: Boolean,
    )

    private companion object : KLogging()

    private val eventListeners = ConcurrentHashMap<Class<*>, MutableList<EventHandler>>()

    override fun <T> register(eventType: Class<T>, eventListener: (payload: T) -> Unit, async: Boolean) {
        val listeners = eventListeners.computeIfAbsent(eventType as Class<*>) { ArrayList() }
        @Suppress("UNCHECKED_CAST")
        listeners += EventHandler(eventListener as (event: Any) -> Unit, async = async)
    }

    override fun <T> publish(eventType: Class<T>, event: T) {
        eventListeners[eventType]?.forEach {
            if (it.async) {
                executorService.submit {
                    tryInvokeEventListener(it.handler, event as Any)
                }
            } else {
                tryInvokeEventListener(it.handler, event as Any)
            }
        }
    }

    private fun tryInvokeEventListener(eventListener: (payload: Any) -> Unit, event: Any) {
        try {
            eventListener(event)
        } catch (e: Exception) {
            logger.error(e) { "Handling event by listener failed" }
        }
    }

}
