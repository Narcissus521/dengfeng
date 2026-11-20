package com.campusfinance.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

data class WalletState(val ledger: Ledger? = null, val busy: Boolean = false, val fatal: String? = null, val error: String? = null)
sealed interface SaveTarget {
    data class NewEntry(val id: String, val month: String) : SaveTarget
    data class Huabei(val month: String, val previousPaid: Long) : SaveTarget
}

class SaveConfirmation(val target: SaveTarget?)

class WalletViewModel(private val repository: WalletRepository, demo: Boolean) : ViewModel() {
    private val mutable = MutableStateFlow(WalletState())
    val state = mutable.asStateFlow()
    private val saved = MutableSharedFlow<SaveConfirmation>(extraBufferCapacity=1)
    val confirmations = saved.asSharedFlow()
    init {
        viewModelScope.launch {
            try {
                repository.prepare(demo)
                repository.ledger.flowOn(Dispatchers.Default).collect { mutable.value = mutable.value.copy(ledger=it, fatal=null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(fatal = e.message ?: "账本无法安全打开")
            }
        }
    }
    fun clearError() { mutable.value = mutable.value.copy(error=null) }
    fun execute(action: suspend WalletRepository.() -> Unit, success: () -> Unit = {}) =
        execute(action, null, success)

    fun execute(action: suspend WalletRepository.() -> Unit, target: SaveTarget?, success: () -> Unit) {
        if (mutable.value.busy || mutable.value.fatal != null) return
        mutable.value = mutable.value.copy(busy=true)
        viewModelScope.launch {
            try { repository.action(); success(); saved.emit(SaveConfirmation(target)) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.value = mutable.value.copy(error = e.message ?: "保存失败，数据未变更")
            } finally { mutable.value = mutable.value.copy(busy=false) }
        }
    }
    override fun onCleared() { repository.db.close() }
}
