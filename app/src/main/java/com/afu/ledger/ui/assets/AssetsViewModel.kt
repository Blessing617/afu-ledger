package com.afu.ledger.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.prefs.AssetPreferences
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.domain.model.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetsViewModel(
    private val accountRepository: AccountRepository,
    private val assetPreferences: AssetPreferences,
) : ViewModel() {

    val uiState: StateFlow<AssetsUiState> = combine(
        accountRepository.observeWithBalance(),
        assetPreferences.amountHidden,
    ) { items, amountHidden ->
        buildState(items, amountHidden)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsUiState(),
    )

    fun createAccount(
        name: String,
        type: AccountType,
        initialBalance: Money,
        colorArgb: Long,
        iconKey: String,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            accountRepository.create(
                Account(
                    name = name.trim(),
                    type = type,
                    initialBalance = initialBalance,
                    colorArgb = colorArgb,
                    iconKey = iconKey,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateAccount(account: Account) {
        if (account.name.isBlank()) return
        viewModelScope.launch { accountRepository.save(account.copy(name = account.name.trim())) }
    }

    /** 删除账户（连同它名下的交易，由数据库外键级联）。 */
    fun deleteAccount(id: Long) {
        viewModelScope.launch { accountRepository.delete(id) }
    }

    /** 删除确认弹窗要显示的交易笔数。 */
    suspend fun countTransactions(id: Long): Int = accountRepository.countTransactions(id)

    /** 长按拖动后调用：重排全部账户。 */
    fun moveAccount(fromIndex: Int, toIndex: Int) {
        val active = uiState.value.active
        if (fromIndex !in active.indices || toIndex !in active.indices) return
        if (fromIndex == toIndex) return
        val reordered = active.map { it.account.id }.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        viewModelScope.launch { accountRepository.reorder(reordered) }
    }

    /** 一键切换资产页金额隐私遮罩；状态写入 DataStore，重启后仍然生效。 */
    fun toggleAmountHidden() {
        viewModelScope.launch {
            assetPreferences.setAmountHidden(!uiState.value.amountHidden)
        }
    }

    private fun buildState(items: List<AccountWithBalance>, amountHidden: Boolean): AssetsUiState {
        val assets = items.filter { it.balance.cents >= 0 }.sumOf { it.balance.cents }
        val liabilities = items.filter { it.balance.cents < 0 }.sumOf { -it.balance.cents }

        return AssetsUiState(
            active = items,
            totalAssets = Money(assets),
            totalLiabilities = Money(liabilities),
            netWorth = Money(assets - liabilities),
            amountHidden = amountHidden,
            loaded = true,
        )
    }
}
