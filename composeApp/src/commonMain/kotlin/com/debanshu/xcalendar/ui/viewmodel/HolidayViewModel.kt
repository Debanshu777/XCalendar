package com.debanshu.xcalendar.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.xcalendar.common.DateUtils
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.usecase.holiday.GetHolidaysForYearUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.RefreshHolidaysUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

data class HolidayUiState(
    val holidays: ImmutableList<Holiday> = persistentListOf(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@KoinViewModel
class HolidayViewModel(
    private val getHolidaysForYearUseCase: GetHolidaysForYearUseCase,
    private val refreshHolidaysUseCase: RefreshHolidaysUseCase
) : ViewModel() {

    // Default country code - could be made configurable
    private val countryCode = "IN"

    // Use shared date utilities
    private val currentYear = DateUtils.getCurrentYear()

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val holidaysFlow = getHolidaysForYearUseCase(countryCode, currentYear)
        .catch { e ->
            _errorMessage.value = "Failed to load holidays: ${e.message}"
            emit(emptyList())
        }

    val uiState: StateFlow<HolidayUiState> = combine(
        holidaysFlow,
        _isLoading,
        _errorMessage
    ) { holidays, isLoading, errorMessage ->
        HolidayUiState(
            holidays = holidays.toImmutableList(),
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HolidayUiState(isLoading = true)
    )

    init {
        initializeHolidays()
    }

    private fun initializeHolidays() {
        viewModelScope.launch {
            holidaysFlow.collectLatest { holidays ->
                if (holidays.isEmpty()) {
                    refreshHolidays()
                }
            }
        }
    }

    fun refreshHolidays() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                refreshHolidaysUseCase(countryCode, currentYear)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to refresh holidays: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

