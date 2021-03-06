package autocoin.balance.blockchain.eth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EthWalletAddressValidatorTest {
    @Test
    fun shouldBeValid() {
        // given
        val sampleEthAddressTakenFromEtherscan = "0x445c9d791b782a7b181194950e0c0ee8c14468f1"
//        val sampleEthAddressTakenFromEtherscan = "0x19ce8df35f56bcabb8426d145b8e7984bef90a22"
        val tested = EthWalletAddressValidator()
        // when
        val isValid = tested.isWalletAddressValid(sampleEthAddressTakenFromEtherscan)
        // then
        assertThat(isValid).isTrue
    }
}
