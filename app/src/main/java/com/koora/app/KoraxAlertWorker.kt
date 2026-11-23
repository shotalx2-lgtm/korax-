package com.koora.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class KoraxAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            KoraxSmartAlerts.poll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
