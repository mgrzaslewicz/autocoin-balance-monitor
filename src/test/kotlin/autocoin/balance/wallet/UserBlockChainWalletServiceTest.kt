package autocoin.balance.wallet

import autocoin.balance.blockchain.eth.EthService
import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UserBlockChainWalletServiceTest {

    @Test
    fun shouldUpdateWalletBalances() {
        // given
        val userAccountId = "a-2-c"
        val walletAddress1 = "wallet-address-1"
        val walletAddress2 = "wallet-address-2"
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.findWalletsByUserAccountId(userAccountId)).thenReturn(
                listOf(
                    UserBlockChainWallet(
                        id = "1-2-3",
                        userAccountId = userAccountId,
                        balance = null,
                        description = null,
                        walletAddress = walletAddress1,
                        currency = "ETH",
                    ),
                    UserBlockChainWallet(
                        id = "4-5-6",
                        userAccountId = userAccountId,
                        balance = BigDecimal("3.0"),
                        description = null,
                        walletAddress = walletAddress2,
                        currency = "ETH",
                    )
                )
            )
        }
        val tested = UserBlockChainWalletService(userBlockChainWalletRepository = { walletRepository },
            ethService = mock<EthService>().apply {
                whenever(this.getEthBalance(walletAddress1)).thenReturn(BigDecimal("2.1"))
                whenever(this.getEthBalance(walletAddress2)).thenReturn(BigDecimal("3.5"))
            })
        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(walletRepository).findWalletsByUserAccountId(userAccountId)
        verify(walletRepository).updateWallet(
            UserBlockChainWallet(
                id = "1-2-3",
                userAccountId = userAccountId,
                balance = BigDecimal("2.1"),
                description = null,
                walletAddress = walletAddress1,
                currency = "ETH",
            )
        )
        verify(walletRepository).updateWallet(
            UserBlockChainWallet(
                id = "4-5-6",
                userAccountId = userAccountId,
                balance = BigDecimal("3.5"),
                description = null,
                walletAddress = walletAddress2,
                currency = "ETH",
            )
        )
    }

    @Test
    fun shouldNotUpdateWalletBalancesWhenCannotGetBalance() {
        // given
        val userAccountId = "a-2-c"
        val walletAddress = "wallet-address-1"
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.findWalletsByUserAccountId(userAccountId)).thenReturn(
                listOf(
                    UserBlockChainWallet(
                        id = "1-2-3",
                        userAccountId = userAccountId,
                        balance = BigDecimal("11.2"),
                        description = null,
                        walletAddress = walletAddress,
                        currency = "ETH",
                    ),
                )
            )
        }
        val tested = UserBlockChainWalletService(userBlockChainWalletRepository = { walletRepository },
            ethService = mock<EthService>().apply {
                whenever(this.getEthBalance(walletAddress)).thenReturn(null)
            })
        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(walletRepository).findWalletsByUserAccountId(userAccountId)
        verify(walletRepository, never()).updateWallet(any())
    }
}
