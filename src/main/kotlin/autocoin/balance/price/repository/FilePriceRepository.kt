package autocoin.balance.price.repository

import autocoin.balance.price.CurrencyPrice
import autocoin.balance.price.CurrencyPriceDto
import automate.profit.autocoin.exchange.currency.CurrencyPair
import automate.profit.autocoin.keyvalue.FileKeyValueRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
import java.nio.file.Path
import kotlin.io.path.absolutePathString


class FilePriceRepository(
    private val fileKeyValueRepository: FileKeyValueRepository,
    private val objectMapper: ObjectMapper,
    private val pricesFolder: Path,
) {
    private companion object : KLogging()

    private val key = "prices"

    private fun CurrencyPrice.toDto() = CurrencyPriceDto(
        price = price.toPlainString(),
        baseCurrency = baseCurrency,
        counterCurrency = counterCurrency,
        timestampMillis = timestampMillis,
    )

    fun getPrices(): Map<CurrencyPair, CurrencyPrice> {
        val latestVersion = fileKeyValueRepository.getLatestVersion(pricesFolder.toFile(), key)
        return if (latestVersion != null) {
            try {
                val prices = objectMapper.readValue(latestVersion.value, object : TypeReference<List<CurrencyPriceDto>>() {}).map { it.toCurrencyPrice() }
                prices.associateBy { it.currencyPair }
            } catch (e: Exception) {
                logger.error { "Could not deserialize prices from ${latestVersion.file.absolutePathString()}" }
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    fun savePrices(prices: Collection<CurrencyPrice>) {
        fileKeyValueRepository.saveNewVersion(
            directory = pricesFolder.toFile(),
            key = key,
            value = objectMapper.writeValueAsString(prices.map { it.toDto() })
        )
        keepLast10Files()
    }

    private fun keepLast10Files() {
        fileKeyValueRepository.keepLastNVersions(directory = pricesFolder.toFile(), key = key, maxVersions = 10)
    }
}
