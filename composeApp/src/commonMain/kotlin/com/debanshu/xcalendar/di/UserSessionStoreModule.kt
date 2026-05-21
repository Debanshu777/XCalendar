package com.debanshu.xcalendar.di

import com.debanshu.xcalendar.data.store.CacheTimestampTracker
import com.debanshu.xcalendar.data.store.EventBookkeeperFactory
import com.debanshu.xcalendar.data.store.EventKey
import com.debanshu.xcalendar.data.store.EventStoreFactory
import com.debanshu.xcalendar.data.store.HolidayKey
import com.debanshu.xcalendar.data.store.HolidayStoreFactory
import com.debanshu.xcalendar.data.store.SingleEventBookkeeperFactory
import com.debanshu.xcalendar.data.store.SingleEventKey
import com.debanshu.xcalendar.data.store.SingleEventStoreFactory
import com.debanshu.xcalendar.data.store.StoreEventValidator
import com.debanshu.xcalendar.data.store.StoreHolidayValidator
import com.debanshu.xcalendar.domain.model.Event
import com.debanshu.xcalendar.domain.model.Holiday
import com.debanshu.xcalendar.domain.repository.EventRepository
import com.debanshu.xcalendar.domain.repository.HolidayRepository
import com.debanshu.xcalendar.domain.repository.IEventRepository
import com.debanshu.xcalendar.domain.repository.IHolidayRepository
import com.debanshu.xcalendar.domain.usecase.event.CreateEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.DeleteEventUseCase
import com.debanshu.xcalendar.domain.usecase.event.GetEventsForDateRangeUseCase
import com.debanshu.xcalendar.domain.usecase.event.UpdateEventUseCase
import com.debanshu.xcalendar.domain.usecase.holiday.GetHolidaysForYearUseCase
import com.debanshu.xcalendar.ui.CalendarViewModel
import com.debanshu.xcalendar.ui.viewmodel.EventViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mobilenativefoundation.store.store5.Bookkeeper
import org.mobilenativefoundation.store.store5.MutableStore
import org.mobilenativefoundation.store.store5.Store

/**
 * Per-[UserSession] Store5 graph: isolated TTL timestamps, bookkeepers, and
 * mutable stores so switching users cannot leak cache state (audit F23 / Phase 9).
 */
val userSessionStoreModule =
    module {
        /**
         * Opens the default [UserSession] Koin scope at process start so Store5
         * repositories exist before any ViewModel is created (Phase 9 / F23).
         *
         * This uses the handwritten DSL because Koin itself is available from
         * the resolution context, not as a normal bean for KSP to inject.
         */
        single<UserSessionScopeHolder>(createdAtStart = true) {
            UserSessionScopeHolder(getKoin())
        }

        scope<UserSession> {
            scoped { CacheTimestampTracker() }
            scoped { StoreHolidayValidator(get()) }
            scoped { StoreEventValidator(get()) }

            scoped<Bookkeeper<EventKey>>(named("eventBookkeeper")) {
                EventBookkeeperFactory.create(get())
            }
            scoped<Bookkeeper<SingleEventKey>>(named("singleEventBookkeeper")) {
                SingleEventBookkeeperFactory.create(get())
            }

            scoped<MutableStore<EventKey, List<Event>>>(named("eventStore")) {
                EventStoreFactory.create(
                    apiService = get(),
                    eventDao = get(),
                    bookkeeper = get(named("eventBookkeeper")),
                    eventValidator = get(),
                )
            }
            scoped<MutableStore<SingleEventKey, Event>>(named("singleEventStore")) {
                SingleEventStoreFactory.create(
                    eventDao = get(),
                    bookkeeper = get(named("singleEventBookkeeper")),
                )
            }
            scoped<Store<HolidayKey, List<Holiday>>> {
                HolidayStoreFactory.create(
                    holidayApiService = get(),
                    holidayDao = get(),
                    holidayValidator = get(),
                )
            }

            scoped<IEventRepository> {
                EventRepository(
                    eventStore = get(named("eventStore")),
                    singleEventStore = get(named("singleEventStore")),
                    eventDao = get(),
                    outbox = get(),
                    userSession = getSource<UserSession>() ?: UserSession(DEFAULT_USER_SESSION_ID),
                    eventValidator = get(),
                )
            }
            scoped<IHolidayRepository> {
                HolidayRepository(
                    holidayStore = get(),
                    holidayValidator = get(),
                )
            }

            factory { GetEventsForDateRangeUseCase(get()) }
            factory { GetHolidaysForYearUseCase(get()) }
            factory { CreateEventUseCase(get()) }
            factory { UpdateEventUseCase(get()) }
            factory { DeleteEventUseCase(get()) }

            viewModel {
                CalendarViewModel(
                    userRepository = get(),
                    calendarRepository = get(),
                    eventRepository = get(),
                    dateStateHolder = get(),
                    getUserCalendarsUseCase = get(),
                    getEventsForDateRangeUseCase = get(),
                    getHolidaysForYearUseCase = get(),
                    getCurrentUserUseCase = get(),
                    holidayCountryResolver = get(),
                )
            }
            viewModel {
                EventViewModel(
                    createEventUseCase = get(),
                    updateEventUseCase = get(),
                    deleteEventUseCase = get(),
                )
            }
        }
    }
