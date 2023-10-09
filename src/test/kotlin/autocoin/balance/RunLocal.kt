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
    setProperty("logging.level", "DEBUG")
    setProperty("PROFILES", "dev,ownDbContainer")
    main(emptyArray())
}
