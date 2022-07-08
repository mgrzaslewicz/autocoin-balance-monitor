package autocoin.balance

import java.lang.System.setProperty

/**
 * Copy this file to src/main and provide settings to run
 * Add limits when running process
-Xmx400M
-XX:+ExitOnOutOfMemoryError
-XX:+HeapDumpOnOutOfMemoryError
 */
fun main() {
    setProperty("SERVICE_NAME", "autocoin-balance-monitor")
    setProperty("EXCHANGE_MEDIATOR_API_URL", "http://localhost:9001")
    setProperty("OAUTH2_API_URL", "http://localhost:9002")
    setProperty("OAUTH_CLIENT_SECRET", "changeme")
    setProperty("USE_METRICS", "false")
    setProperty("TELEGRAF_HOSTNAME", "localhost")
    setProperty("logging.level", "DEBUG")
    main(emptyArray())
}
