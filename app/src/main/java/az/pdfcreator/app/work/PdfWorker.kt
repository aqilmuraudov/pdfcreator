package az.pdfcreator.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import az.pdfcreator.app.MainActivity
import az.pdfcreator.app.R
import az.pdfcreator.app.data.HistoryRepository
import az.pdfcreator.app.data.OutputSaver
import az.pdfcreator.app.model.HistoryEntry
import az.pdfcreator.app.model.PdfJob
import az.pdfcreator.app.pdf.PdfGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notificationId = 4201
    private var lastNotify = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobPath = inputData.getString(KEY_JOB) ?: return@withContext Result.failure()
        val jobFile = File(jobPath)
        val tmp = File(applicationContext.cacheDir, "out/${id}.pdf")
        try {
            val job = PdfJob.fromJson(jobFile.readText())
            // Foreground keeps long jobs alive; it may be refused if the app is in background (Android 12+).
            runCatching { setForeground(foregroundInfo(0, job.items.size)) }

            val limited = PdfGenerator(applicationContext).generateWithLimit(
                job.items, job.options, tmp, signatureFile(applicationContext),
            ) { done, total, pass ->
                setProgress(workDataOf(PROGRESS_DONE to done, PROGRESS_TOTAL to total, PROGRESS_PASS to pass))
                val now = System.currentTimeMillis()
                if (now - lastNotify > 700 || done == total) {
                    lastNotify = now
                    runCatching { notify(foregroundInfo(done, total)) }
                }
            }
            val result = limited.result

            val saved = OutputSaver.save(applicationContext, tmp, job.options.fileName)
            HistoryRepository(applicationContext).add(
                HistoryEntry(
                    name = saved.name,
                    uri = saved.uri.toString(),
                    filePath = saved.filePath,
                    sizeBytes = saved.size,
                    pages = result.pages,
                    createdAt = System.currentTimeMillis(),
                    protected = job.options.password.isNotEmpty(),
                ),
            )
            showDoneNotification(saved.name)
            Result.success(
                workDataOf(
                    OUT_URI to saved.uri.toString(),
                    OUT_NAME to saved.name,
                    OUT_PAGES to result.pages,
                    OUT_SIZE to saved.size,
                    OUT_SKIPPED to result.skipped,
                    OUT_PROTECTED to job.options.password.isNotEmpty(),
                    OUT_LOCATION to saved.location,
                    OUT_OVER_LIMIT to limited.overLimit,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: PdfGenerator.NothingToWriteException) {
            Result.failure(workDataOf(OUT_ERROR to ERROR_NO_IMAGES))
        } catch (e: OutOfMemoryError) {
            Result.failure(workDataOf(OUT_ERROR to ERROR_MEMORY))
        } catch (e: Exception) {
            Result.failure(workDataOf(OUT_ERROR to (e.message ?: e.javaClass.simpleName)))
        } finally {
            tmp.delete()
            jobFile.delete()
        }
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureChannel(applicationContext)
        val ctx = applicationContext
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_creating))
            .setContentText(if (total > 0) ctx.getString(R.string.progress_images, done, total) else null)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(ctx))
            .addAction(0, ctx.getString(R.string.cancel), androidx.work.WorkManager.getInstance(ctx).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun notify(info: ForegroundInfo) {
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            NotificationManagerCompat.from(applicationContext).notify(info.notificationId, info.notification)
        }
    }

    private fun showDoneNotification(name: String) {
        val ctx = applicationContext
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(ctx.getString(R.string.notif_done))
            .setContentText(name)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(notificationId + 1, n) }
    }

    companion object {
        const val KEY_JOB = "job_path"
        const val PROGRESS_DONE = "done"
        const val PROGRESS_TOTAL = "total"
        const val OUT_URI = "uri"
        const val OUT_NAME = "name"
        const val OUT_PAGES = "pages"
        const val OUT_SIZE = "size"
        const val OUT_SKIPPED = "skipped"
        const val OUT_PROTECTED = "protected"
        const val OUT_LOCATION = "location"
        const val OUT_OVER_LIMIT = "over_limit"
        const val PROGRESS_PASS = "pass"

        /** The user's saved signature (transparent PNG), see SignaturePad. */
        fun signatureFile(context: Context) = File(context.filesDir, "signature.png")
        const val OUT_ERROR = "error"
        const val ERROR_NO_IMAGES = "no_images"
        const val ERROR_MEMORY = "memory"
        const val CHANNEL_ID = "pdf_progress"
        const val UNIQUE_NAME = "create_pdf"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
                    )
                }
            }
        }

        private fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
