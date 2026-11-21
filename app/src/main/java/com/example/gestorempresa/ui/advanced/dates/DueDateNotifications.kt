package com.example.gestorempresa.ui.advanced.dates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.gestorempresa.MainActivity
import com.example.gestorempresa.R
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Avisos de vencimientos fuera de la app: una vez al día (sobre las 9:00) revisa las fechas
 * de la empresa y muestra una notificación con lo que está vencido o a punto de vencer.
 * Usa las preferencias de Ajustes (activar avisos, días de antelación y avisos de tareas).
 */
object DueDateNotifications {
    private const val CHANNEL_ID = "vencimientos"
    private const val WORK_NAME = "aviso_vencimientos"
    private const val NOTIFICATION_ID = 4021
    internal const val KEY_COMPANY = "empresa"
    internal const val KEY_DAYS = "dias"
    internal const val KEY_EXPIRIES = "vencimientos"
    internal const val KEY_TASKS = "tareas"

    /** Programa (o actualiza) la revisión diaria. Si los dos tipos de aviso están desactivados, la cancela. */
    fun schedule(context: Context, companyId: Long, warnDays: Int, expiries: Boolean, tasks: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!expiries && !tasks) { work.cancelUniqueWork(WORK_NAME); return }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DueDateWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.between(now, next))
            .setInputData(inputData(companyId, warnDays, expiries, tasks))
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Revisa las fechas ahora mismo (botón del calendario). */
    fun runNow(context: Context, companyId: Long, warnDays: Int, expiries: Boolean, tasks: Boolean) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<DueDateWorker>()
            .setInputData(inputData(companyId, warnDays, expiries, tasks)).build())
    }

    /** Al cerrar sesión no se deben mostrar datos de la empresa. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun inputData(companyId: Long, warnDays: Int, expiries: Boolean, tasks: Boolean) = workDataOf(
        KEY_COMPANY to companyId, KEY_DAYS to warnDays, KEY_EXPIRIES to expiries, KEY_TASKS to tasks)

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun show(context: Context, items: List<DueItem>, today: LocalDate, warnDays: Int) {
        val manager = NotificationManagerCompat.from(context)
        if (items.isEmpty() || !canNotify(context)) { manager.cancel(NOTIFICATION_ID); return }
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Vencimientos y tareas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reconocimientos médicos, cursos, contratos, documentos y tareas próximos a vencer"
                })
        }
        val overdue = items.count { it.status(today, warnDays) == DueStatus.VENCIDO }
        val title = when {
            overdue > 0 -> "$overdue vencido${if (overdue > 1) "s" else ""} · ${items.size - overdue} próximo${if (items.size - overdue != 1) "s" else ""}"
            else -> "${items.size} fecha${if (items.size > 1) "s" else ""} próxima${if (items.size > 1) "s" else ""} a vencer"
        }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        items.take(6).forEach { style.addLine("${it.date} · ${it.title} (${it.detail})") }
        if (items.size > 6) style.setSummaryText("y ${items.size - 6} más")
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_due)
            .setContentTitle(title)
            .setContentText(items.first().let { "${it.date} · ${it.title}" })
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { manager.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { }
    }
}

/** Trabajo en segundo plano que lee la base de datos de la empresa y muestra el aviso. */
class DueDateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val companyId = inputData.getLong(DueDateNotifications.KEY_COMPANY, 0)
        if (companyId <= 0) return Result.failure()
        val warnDays = inputData.getInt(DueDateNotifications.KEY_DAYS, 15)
        val dao = HomeDatabase.forCompany(applicationContext, companyId).homeDao()
        val state = HomeWorkspaceState(
            employees = dao.employees().first(), projects = dao.projects().first(), loading = false,
            subcontractors = dao.subcontractors().first(), suppliers = dao.suppliers().first(), materials = dao.materials().first(),
            obligations = dao.obligations().first(), tasks = dao.tasks().first(), attachments = dao.attachments().first()
        )
        val today = LocalDate.now()
        val items = state.pendingWarnings(today, warnDays,
            inputData.getBoolean(DueDateNotifications.KEY_EXPIRIES, true), inputData.getBoolean(DueDateNotifications.KEY_TASKS, true))
        DueDateNotifications.show(applicationContext, items, today, warnDays)
        return Result.success()
    }
}
