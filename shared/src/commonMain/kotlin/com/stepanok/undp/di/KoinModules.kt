package com.stepanok.undp.di

import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.IdGenerator
import com.stepanok.undp.core.SystemClock
import com.stepanok.undp.core.UuidGenerator
import com.stepanok.undp.core.connectivity.ConnectivityObserver
import com.stepanok.undp.core.connectivity.createConnectivityObserver
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.location.createLocationProvider
import com.stepanok.undp.core.ml.DamageClassifier
import com.stepanok.undp.core.ml.createDamageClassifier
import com.stepanok.undp.core.offline.createDownloadQueue
import com.stepanok.undp.data.remote.BeaconApi
import com.stepanok.undp.data.remote.RemoteCrisisRepository
import com.stepanok.undp.data.remote.RemoteProfileRepository
import com.stepanok.undp.data.remote.RemoteReportRepository
import com.stepanok.undp.domain.repository.CrisisRepository
import com.stepanok.undp.domain.repository.DownloadQueue
import com.stepanok.undp.domain.repository.ProfileRepository
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.domain.repository.SyncManager
import com.stepanok.undp.translation.LanguageDetector
import com.stepanok.undp.translation.ScriptLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

@OptIn(kotlin.time.ExperimentalTime::class)
val coreModule: Module = module {
    single<AppClock> { SystemClock() }
    single<IdGenerator> { UuidGenerator() }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<ConnectivityObserver> { createConnectivityObserver() }
    single<LocationProvider> { createLocationProvider() }
    single<DamageClassifier> { createDamageClassifier() }
    single<LanguageDetector> { ScriptLanguageDetector() }
}

@OptIn(kotlin.time.ExperimentalTime::class)
val dataModule: Module = module {
    // LIVE backend (Ktor → Go/PostGIS). The mock→server swap boundary: only this
    // module changes; every screen still depends on the same repository interfaces.
    single { BeaconApi() }
    // One instance is both the live ReportRepository and the real outbox/SyncManager.
    single { RemoteReportRepository(get(), get(), get()) }
    single<ReportRepository> { get<RemoteReportRepository>() }
    single<SyncManager> { get<RemoteReportRepository>() }
    single<CrisisRepository> { RemoteCrisisRepository(get()) }
    single<ProfileRepository> { RemoteProfileRepository(get()) }
    single<DownloadQueue> { createDownloadQueue() } // real MapLibre offline-pack download
}

/** ScreenModel factories are registered here as features are built. */
val featureModule: Module = module {
    factory { com.stepanok.undp.feature.map.MapScreenModel(get(), get(), get(), get(), get()) }
    factory { com.stepanok.undp.feature.capture.CaptureFlowScreenModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { com.stepanok.undp.feature.reports.ReportsScreenModel(get(), get(), get(), get()) }
    factory { (id: String) -> com.stepanok.undp.feature.reportdetail.ReportDetailScreenModel(id, get(), get()) }
    factory { com.stepanok.undp.feature.profile.ProfileScreenModel(get(), get()) }
    factory { com.stepanok.undp.feature.offline.OfflineDownloadsScreenModel(get(), get(), get()) }
}

fun beaconModules(): List<Module> = listOf(coreModule, dataModule, featureModule)
