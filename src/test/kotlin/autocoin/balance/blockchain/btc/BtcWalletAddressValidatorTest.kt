package autocoin.balance.blockchain.btc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BtcWalletAddressValidatorTest {
    @Test
    fun shouldBeValid() {
        // given
        val sampleBtcAddress = "bc1qhq66uyw53n7sfk200czg556mdmdg8t7nvgdkdd"
        val tested = BtcWalletAddressValidator()
        // when
        val isValid = tested.isWalletAddressValid(sampleBtcAddress)
        // then
        assertThat(isValid).isTrue
    }
}
