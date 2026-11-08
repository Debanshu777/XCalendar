package com.debanshu.xcalendar.ui

import androidx.lifecycle.ViewModelStore
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IHolidayRepository
import com.debanshu.xcalendar.domain.usecase.calendar.GetUserCalendarsUseCase
import com.debanshu.xcalendar.domain.usecase.event.GetEventsForDateRangeUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.GetHolidaysForYearUseCase
import com.debanshu.xcalendar.domain.usecase.user.GetCurrentUserUseCase
import com.debanshu.xcalendar.test.FakeCalendarRepository
import com.debanshu.xcalendar.test.FakeEventRepository
import com.debanshu.xcalendar.test.FakeHolidayRepository
import com.debanshu.xcalendar.test.FakeUserRepository
import com.debanshu.xcalendar.test.TestDataFactory
import com.debanshu.xcalendar.ui.state.DateStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    @Test
    fun cachedAccountIsAvailableEvenWhenEventAndHolidayStoresHaveNoData() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val users = FakeUserRepository(listOf(TestDataFactory.createUser(id = "user_id")))
            val calendars = FakeCalendarRepository()
            val events = object : IEventRepository by FakeEventRepository() {
                override fun getEventsForCalendarsInRange(userId: String, start: Long, end: Long): Flow<List<Event>> = emptyFlow()
            }
            val holidays = object : IHolidayRepository by FakeHolidayRepository() {
                override fun getHolidaysForYear(countryCode: String, year: Int): Flow<List<Holiday>> = emptyFlow()
            }
            val vm = CalendarViewModel(users, calendars, events, DateStateHolder(),
                GetUserCalendarsUseCase(calendars), GetEventsForDateRangeUseCase(events),
                GetHolidaysForYearUseCase(holidays), GetCurrentUserUseCase())
            store.put("calendar", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            advanceUntilIdle()
            assertTrue(vm.uiState.value.accounts.isNotEmpty(), "An empty remote store must not gate the Add Event account")
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
