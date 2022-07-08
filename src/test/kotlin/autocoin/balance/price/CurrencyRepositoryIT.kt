package autocoin.balance.price

import autocoin.TestDb
import autocoin.balance.wallet.blockchain.UserBlockChainWallet
import autocoin.balance.wallet.blockchain.UserBlockChainWalletRepository
import autocoin.balance.wallet.exchange.UserExchangeWallet
import autocoin.balance.wallet.exchange.UserExchangeWalletRepository
import org.assertj.core.api.Assertions.assertThat
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class CurrencyRepositoryIT {
    private lateinit var startedDatabase: TestDb.StartedDatabase
    private lateinit var jdbi: Jdbi

    @BeforeEach
    fun setup() {
        startedDatabase = TestDb.startDatabase()
        jdbi = startedDatabase.jdbi
    }

    @AfterEach
    fun cleanup() {
        startedDatabase.container.stop()
    }

    @Test
    fun shouldGetUniqueCurrencies() {
        // given
        val exchangeWalletRepository = jdbi.onDemand(UserExchangeWalletRepository::class.java)
        val blockchainWalletRepository = jdbi.onDemand(UserBlockChainWalletRepository::class.java)
        val currencyRepository = jdbi.onDemand(CurrencyRepository::class.java)
        blockchainWalletRepository.insertWallet(
            UserBlockChainWallet(
                walletAddress = "test1",
                currency = "ETH",
                userAccountId = UUID.randomUUID().toString(),
                description = null,
                balance = null,
            )
        )
        blockchainWalletRepository.insertWallet(
            UserBlockChainWallet(
                walletAddress = "test2",
                currency = "ETH",
                userAccountId = UUID.randomUUID().toString(),
                description = null,
                balance = null,
            )
        )
        blockchainWalletRepository.insertWallet(
            UserBlockChainWallet(
                walletAddress = "test3",
                currency = "BTC",
                userAccountId = UUID.randomUUID().toString(),
                description = null,
                balance = null,
            )
        )
        exchangeWalletRepository.insertWallet(
            UserExchangeWallet(
                currency = "XRP",
                userAccountId = UUID.randomUUID().toString(),
                balance = BigDecimal("10.6"),
                exchange = "bittrex",
                exchangeUserId = UUID.randomUUID().toString(),
                amountInOrders = BigDecimal("1.7"),
                amountAvailable = BigDecimal("8.9"),
            )
        )
        exchangeWalletRepository.insertWallet(
            UserExchangeWallet(
                currency = "XRP",
                userAccountId = UUID.randomUUID().toString(),
                balance = BigDecimal("10.6"),
                exchange = "binance",
                exchangeUserId = UUID.randomUUID().toString(),
                amountInOrders = BigDecimal("1.7"),
                amountAvailable = BigDecimal("8.9"),
            )
        )

        // when
        val currencies = currencyRepository.selectUniqueWalletCurrencies()
        // then
        assertThat(currencies).containsExactly("BTC", "ETH", "XRP")
    }


}
