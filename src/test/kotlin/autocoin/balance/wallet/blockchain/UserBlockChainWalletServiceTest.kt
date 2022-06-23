package autocoin.balance.wallet.blockchain

import autocoin.balance.api.controller.UpdateBlockchainWalletRequestDto
import autocoin.balance.blockchain.MultiBlockchainWalletService
import autocoin.balance.blockchain.eth.EthService
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
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
            whenever(this.findManyByUserAccountId(userAccountId)).thenReturn(
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
        val tested = UserBlockChainWalletService(
            userBlockChainWalletRepository = { walletRepository },
            multiBlockchainWalletService = MultiBlockchainWalletService(
                blockchainWalletServices = listOf(mock<EthService>().apply {
                    whenever(this.getBalance(walletAddress1)).thenReturn(BigDecimal("2.1"))
                    whenever(this.getBalance(walletAddress2)).thenReturn(BigDecimal("3.5"))
                    whenever(this.currency).thenReturn("ETH")
                }
                )
            )
        )
        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(walletRepository).findManyByUserAccountId(userAccountId)
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
            whenever(this.findManyByUserAccountId(userAccountId)).thenReturn(
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
            multiBlockchainWalletService = MultiBlockchainWalletService(
                blockchainWalletServices = listOf(
                    mock<EthService>().apply {
                        whenever(this.getBalance(walletAddress)).thenReturn(null)
                        whenever(this.currency).thenReturn("ETH")
                    }
                )
            )
        )

        // when
        tested.refreshWalletBalances(userAccountId)
        // then
        verify(walletRepository).findManyByUserAccountId(userAccountId)
        verify(walletRepository, never()).updateWallet(any())
    }

    @Test
    fun shouldUpdateWallet() {
        // given
        val userAccountId = "a-2-c"
        val walletAddress = "wallet-address-1"
        val walletId = "1-2-3"
        val wallet = UserBlockChainWallet(
            id = walletId,
            userAccountId = userAccountId,
            balance = BigDecimal("11.2"),
            description = null,
            walletAddress = walletAddress,
            currency = "ETH",
        )
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.existsByUserAccountIdAndId(userAccountId, walletId)).thenReturn(true)
            whenever(this.findOneById(walletId)).thenReturn(wallet)
        }
        val tested = UserBlockChainWalletService(userBlockChainWalletRepository = { walletRepository }, multiBlockchainWalletService = mock())
        // when
        val newWalletAddress = "wallet-address-2"
        val updateResult = tested.updateWallet(
            userAccountId,
            UpdateBlockchainWalletRequestDto(
                id = walletId,
                walletAddress = newWalletAddress,
                description = "sample description",
                currency = "BTC"
            )
        )
        // then
        verify(walletRepository).updateWallet(
            eq(
                UserBlockChainWallet(
                    id = walletId,
                    userAccountId = userAccountId,
                    balance = BigDecimal("11.2"),
                    description = "sample description",
                    walletAddress = newWalletAddress,
                    currency = "BTC",
                )
            )
        )
        assertThat(updateResult.isSuccessful()).isTrue
    }

    @Test
    fun shouldAddWallet() {
        // given
        val userAccountId = "a-2-c"
        val walletAddress = "wallet-address-1"
        val walletId = "1-2-3"
        val wallet = UserBlockChainWallet(
            id = walletId,
            userAccountId = userAccountId,
            balance = null,
            description = null,
            walletAddress = walletAddress,
            currency = "ETH",
        )
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.existsByUserAccountIdAndWalletAddress(userAccountId, walletAddress)).thenReturn(false)
        }
        val tested = UserBlockChainWalletService(
            userBlockChainWalletRepository = { walletRepository },
            multiBlockchainWalletService = mock<MultiBlockchainWalletService>().apply {
                whenever(this.getBalance("ETH", walletAddress)).thenReturn(BigDecimal("456"))
            }
        )
        // when
        tested.addWallet(userAccountId, wallet)
        // then
        verify(walletRepository).insertWallet(eq(wallet))
        verify(walletRepository).updateWallet(eq(wallet.copy(balance = BigDecimal("456"))))
    }

    @Test
    fun shouldNotAddWalletWhenUserAlreadyHasWalletWithAddress() {
        // given
        val userAccountId = "a-2-c"
        val walletAddress = "wallet-address-1"
        val walletId = "1-2-3"
        val wallet = UserBlockChainWallet(
            id = walletId,
            userAccountId = userAccountId,
            balance = null,
            description = null,
            walletAddress = walletAddress,
            currency = "ETH",
        )
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.existsByUserAccountIdAndWalletAddress(userAccountId, walletAddress)).thenReturn(true)
        }
        val tested = UserBlockChainWalletService(
            userBlockChainWalletRepository = { walletRepository },
            multiBlockchainWalletService = mock()
        )
        // when
        val addResult = tested.addWallet(userAccountId, wallet)
        // then
        assertThat(addResult.userAlreadyHasWalletWithThisAddress).isTrue
        verify(walletRepository, never()).insertWallet(eq(wallet))
        verify(walletRepository, never()).updateWallet(eq(wallet.copy(balance = BigDecimal("456"))))
    }

    @Test
    fun shouldNotUpdateWalletBelongingToOtherUserOrNonExisting() {
        // given
        val userAccountId = "a-2-c"
        val walletId = "1-2-3"
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.existsByUserAccountIdAndId(userAccountId, walletId)).thenReturn(false)
        }
        val tested = UserBlockChainWalletService(userBlockChainWalletRepository = { walletRepository }, multiBlockchainWalletService = mock())
        // when
        val updateResult = tested.updateWallet(
            userAccountId,
            UpdateBlockchainWalletRequestDto(
                id = walletId,
                walletAddress = "new wallet address",
                description = "sample description",
                currency = "BTC"
            )
        )
        // then
        assertThat(updateResult.isSuccessful()).isFalse
        assertThat(updateResult.userHasNoWalletWithGivenId).isTrue
        verify(walletRepository, never()).updateWallet(any())
    }

    @Test
    fun shouldNotUpdateWalletWhenUserAlreadyHasAnotherOneWithGivenWalletAddress() {
        // given
        val userAccountId = "a-2-c"
        val walletAddress = "wallet-address-1"
        val newWalletAddress = "wallet-address-2"
        val walletId = "1-2-3"
        val wallet = UserBlockChainWallet(
            id = walletId,
            userAccountId = userAccountId,
            balance = BigDecimal("11.2"),
            description = null,
            walletAddress = walletAddress,
            currency = "ETH",
        )
        val walletRepository = mock<UserBlockChainWalletRepository>().apply {
            whenever(this.existsByUserAccountIdAndId(userAccountId, walletId)).thenReturn(true)
            whenever(this.findOneById(walletId)).thenReturn(wallet)
            whenever(this.existsByUserAccountIdAndWalletAddress(userAccountId, newWalletAddress)).thenReturn(true)
        }
        val tested = UserBlockChainWalletService(userBlockChainWalletRepository = { walletRepository }, multiBlockchainWalletService = mock())
        // when
        val updateResult = tested.updateWallet(
            userAccountId,
            UpdateBlockchainWalletRequestDto(
                id = walletId,
                walletAddress = newWalletAddress,
                description = "sample description",
                currency = "BTC"
            )
        )
        // then
        assertThat(updateResult.isSuccessful()).isFalse
        assertThat(updateResult.userAlreadyHasWalletWithThisAddress).isTrue
        verify(walletRepository, never()).updateWallet(any())
    }
}
