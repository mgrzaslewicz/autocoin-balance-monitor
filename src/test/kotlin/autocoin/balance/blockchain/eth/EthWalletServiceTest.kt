package autocoin.balance.blockchain.eth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EthWalletServiceTest {
    @Test
    fun shouldGetBalance() {
        // given
        val ethService = Web3EthService()
        // when
        val balance = ethService.getBalance("0xDBC05B1ECB4FDAEF943819C0B04E9EF6DF4BABD6")
        // then
        assertThat(balance).isZero()
    }
}
