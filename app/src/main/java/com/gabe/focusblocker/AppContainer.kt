package com.gabe.focusblocker

import android.app.Application
import androidx.room.Room
import com.gabe.focusblocker.data.AppDatabase
import com.gabe.focusblocker.engine.EmergencyAllowlist
import com.gabe.focusblocker.engine.EntitlementManager
import com.gabe.focusblocker.engine.PinManager
import com.gabe.focusblocker.engine.RuleEngine
import com.gabe.focusblocker.repository.BlockingRepository
import com.gabe.focusblocker.repository.InstalledAppsRepository
import com.gabe.focusblocker.repository.RuleSetRepository
import com.gabe.focusblocker.repository.ScheduledLockRepository
import com.gabe.focusblocker.repository.SessionRepository
import com.gabe.focusblocker.repository.SettingsRepository

class AppContainer(application: Application) {
    val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "focus-blocker.db"
    )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()

    val settingsRepository = SettingsRepository(application)
    val entitlementManager = EntitlementManager()
    val pinManager = PinManager(settingsRepository)
    val installedAppsRepository = InstalledAppsRepository(application)
    val ruleSetRepository = RuleSetRepository(database)
    val sessionRepository = SessionRepository(database.activeSessionDao())
    val scheduledLockRepository = ScheduledLockRepository(application, database.scheduledLockDao())
    private val emergencyAllowlist = EmergencyAllowlist(application, settingsRepository)
    private val ruleEngine = RuleEngine()
    val blockingRepository = BlockingRepository(database, emergencyAllowlist, ruleEngine)
}
