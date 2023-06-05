package autocoin.balance.api.controller

import autocoin.TestServer.Companion.startTestServer
import autocoin.balance.app.ObjectMapperProvider
import autocoin.balance.blockchain.eth.EthService
import autocoin.balance.blockchain.eth.EthWalletAddressValidator
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class EthWalletControllerIT {

    private val httpClientWithoutAuthorization = OkHttpClient()
    private val objectMapper = ObjectMapperProvider().createObjectMapper()

    @Test
    fun shouldGetEthWalletBalance() {
        // given
        val sampleEthAddressTakenFromEtherscan = "0x19ce8df35f56bcabb8426d145b8e7984bef90a22"
        val ethWalletController = EthWalletController(
            objectMapper = objectMapper,
            ethService = mock<EthService>().apply {
                whenever(this.getEthBalance(sampleEthAddressTakenFromEtherscan)).thenReturn(BigDecimal("0.067"))
            },
            oauth2BearerTokenAuthHandlerWrapper = NoopHttpHandlerWrapper(),
            ethWalletAddressValidator = EthWalletAddressValidator(),
        )
        val startedServer = startTestServer(ethWalletController)
        val request = Request.Builder().url(startedServer.resolveUrl("/eth/wallet/$sampleEthAddressTakenFromEtherscan"))
            .build()
        // when
        val response = httpClientWithoutAuthorization.newCall(request).execute()
        // then
        response.use {
            assertThat(response.isSuccessful).isTrue
            val ethWalletBalanceDto = objectMapper.readValue(it.body?.string(), EthWalletBalanceResponseDto::class.java)
            assertThat(ethWalletBalanceDto.balance).isEqualTo("0.067")
            assertThat(ethWalletBalanceDto.ethWalletAddress).isEqualTo(sampleEthAddressTakenFromEtherscan)
        }
        startedServer.stop()
    }
}
