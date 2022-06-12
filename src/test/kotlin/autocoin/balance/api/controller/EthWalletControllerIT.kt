package autocoin.balance.api.controller

import autocoin.balance.api.ServerBuilder
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.blockchain.eth.EthService
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import me.alexpanov.net.FreePortFinder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EthWalletControllerIT {

    private fun getFreePort() = FreePortFinder.findFreeLocalPort()
    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()

    @Test
    fun shouldGetEthWalletBalance() {
        // given
        val port = getFreePort()
        val sampleEthAddressTakenFromEtherscan = "0x19ce8df35f56bcabb8426d145b8e7984bef90a22"
        val ethWalletController = EthWalletController(
            objectMapper = objectMapper,
            ethService = mock<EthService>().apply {
                whenever(this.getEthBalance(sampleEthAddressTakenFromEtherscan)).thenReturn(BigDecimal("0.067"))
            },
            oauth2BearerTokenAuthHandlerWrapper = NoopHttpHandlerWrapper(),
            ethWalletAddressValidator = EthWalletAddressValidator(),
        )
        val request = Request.Builder()
            .url("http://localhost:$port/eth/wallet/$sampleEthAddressTakenFromEtherscan")
            .build()
        val serverBuilder = ServerBuilder(
            appServerPort = port,
            apiControllers = listOf(ethWalletController),
            metricsService = mock(),
        )

        val server = serverBuilder.build()
        server.start()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        response.use {
            assertThat(response.isSuccessful).isTrue
            val ethWalletBalanceDto = objectMapper.readValue(it.body?.string(), EthWalletBalanceDto::class.java)
            assertThat(ethWalletBalanceDto.balance).isEqualTo("0.067")
            assertThat(ethWalletBalanceDto.ethWalletAddress).isEqualTo(sampleEthAddressTakenFromEtherscan)
        }
        server.stop()
    }
}
