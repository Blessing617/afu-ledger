package com.afu.ledger.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afu.ledger.data.repository.AccountRepository
import com.afu.ledger.domain.model.Account
import com.afu.ledger.domain.model.AccountType
import com.afu.ledger.domain.model.AccountWithBalance
import com.afu.ledger.domain.model.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val uiState: StateFlow<AssetsUiState> = accountRepository.observeWithBalance()
        .map { buildState(it) }
        .stateIn(
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

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { accountRepository.setArchived(id, archived) }
    }

    private fun buildState(items: List<AccountWithBalance>): AssetsUiState {
        val active = items.filter { !it.account.archived }
        val archived = items.filter { it.account.archived }

        val assets = active.filter { it.balance.cents >= 0 }.sumOf { it.balance.cents }
        val liabilities = active.filter { it.balance.cents < 0 }.sumOf { -it.balance.cents }

        return AssetsUiState(
            active = active,
            archived = archived,
            totalAssets = Money(assets),
            totalLiabilities = Money(liabilities),
            netWorth = Money(assets - liabilities),
            loaded = true,
        )
    }
}
