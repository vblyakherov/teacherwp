package com.kubyshka.teacherworkspace.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

private const val AUTO_BACKUP_WORK_NAME = "auto_backup_work"

enum class AutoBackupPeriod(val key: String, val duration: Duration) {
    DAILY("daily", Duration.ofDays(1)),
    WEEKLY("weekly", Duration.ofDays(7)),
    EVERY_THREE_HOURS("every_three_hours", Duration.ofHours(3)),
    MONTHLY("monthly", Duration.ofDays(30));

    companion object {
        fun fromKey(key: String?): AutoBackupPeriod? =
            entries.firstOrNull { it.key == key }
    }
}

class AutoBackupManager(private val context: Context) {

    fun schedule(period: AutoBackupPeriod) {
        val duration = period.duration
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            duration.toHours(),
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
