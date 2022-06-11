package autocoin.balance.blockchain.eth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EthWalletAddressValidatorTest {
    @Test
    fun shouldBeValid() {
        // given
        val randomAddressTakenFromEtherscan = "0x19ce8df35f56bcabb8426d145b8e7984bef90a22"
        val tested = EthWalletAddressValidator()
        //
        val isValid = tested.isWalletAddressValid(randomAddressTakenFromEtherscan)
        // then
        assertThat(isValid).isTrue
    }
}
