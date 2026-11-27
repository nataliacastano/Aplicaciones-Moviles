package com.example.gestorempresa.ui.advanced.dates

import com.example.gestorempresa.ui.advanced.documents.categoryLabel
import com.example.gestorempresa.ui.home.CatalogKind
import com.example.gestorempresa.ui.home.CatalogSelection
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import java.time.LocalDate

/** Tipo de fecha que se controla. [isExpiry] indica si es un vencimiento (caduca) o una fecha programada. */
enum class DueKind(val label: String, val isExpiry: Boolean) {
    MEDICO("Reconocimiento médico", true),
    CURSO("Curso", true),
    DOCUMENTO("Documentación", true),
    CONTRATO("Contrato / documento adjunto", true),
    INICIO("Inicio de servicio o proyecto", false),
    FIN("Finalización prevista", false),
    TAREA("Tarea programada", false)
}

/**
 * Estado de una fecha. Los vencimientos (médicos, cursos, documentos, contratos) son VIGENTE,
 * PROXIMO o VENCIDO; las fechas programadas (tareas, inicio y fin de proyectos) son PROGRAMADO,
 * PROXIMO o VENCIDO. Lo que vence HOY todavía es válido hoy: cuenta como próximo, no como vencido.
 */
enum class DueStatus(val label: String) { VENCIDO("Vencido"), PROXIMO("Próximo a vencer"), VIGENTE("Vigente"), PROGRAMADO("Programado"), HECHO("Completado") }

/** Una fecha importante de la empresa. [target] es la ficha que se abre al tocarla, si la hay. */
data class DueItem(
    val date: LocalDate,
    val kind: DueKind,
    val title: String,
    val detail: String,
    val done: Boolean = false,
    val target: CatalogSelection? = null
) {
    fun status(today: LocalDate, warnDays: Int): DueStatus = when {
        done -> DueStatus.HECHO
        date < today -> if (kind == DueKind.INICIO) DueStatus.HECHO else DueStatus.VENCIDO
        date <= today.plusDays(warnDays.toLong()) -> DueStatus.PROXIMO
        kind.isExpiry -> DueStatus.VIGENTE
        else -> DueStatus.PROGRAMADO
    }
}

private fun parse(value: String?): LocalDate? = try { value?.let(LocalDate::parse) } catch (_: Exception) { null }

/**
 * Reúne todas las fechas que hay que controlar: reconocimientos médicos, cursos, documentación,
 * contratos y documentos adjuntos con caducidad, fechas de servicios y proyectos y tareas programadas.
 */
fun HomeWorkspaceState.dueItems(): List<DueItem> {
    val items = mutableListOf<DueItem>()
    fun workerTarget(kind: String, id: Long) = CatalogSelection(if (kind == "EMPLEADO") CatalogKind.EMPLOYEES else CatalogKind.SUBCONTRACTORS, id)
    obligations.forEach { o ->
        val date = parse(o.dueDate) ?: return@forEach
        val kind = when (o.kind) { "MEDICO" -> DueKind.MEDICO; "CURSO" -> DueKind.CURSO; else -> DueKind.DOCUMENTO }
        items += DueItem(date, kind, o.title, workerName(o.workerKind, o.workerId), o.resolved, workerTarget(o.workerKind, o.workerId))
    }
    attachments.forEach { file ->
        val date = parse(file.expiresAt) ?: return@forEach
        val target = when (file.ownerKind) {
            "EMPLEADO", "SUBCONTRATADO" -> workerTarget(file.ownerKind, file.ownerId)
            "PROYECTO" -> CatalogSelection(CatalogKind.PROJECTS, file.ownerId)
            "PROVEEDOR" -> CatalogSelection(CatalogKind.SUPPLIERS, file.ownerId)
            "MATERIAL" -> CatalogSelection(CatalogKind.MATERIALS, file.ownerId)
            else -> null
        }
        items += DueItem(date, DueKind.CONTRATO, file.name, "${categoryLabel(file.category)} · ${attachmentOwnerName(file)}", target = target)
    }
    projects.forEach { p ->
        val target = CatalogSelection(CatalogKind.PROJECTS, p.id)
        parse(p.fechaInicio)?.let { items += DueItem(it, DueKind.INICIO, p.titulo, p.direccionUbicacion, target = target) }
        parse(p.fechaFin)?.let { items += DueItem(it, DueKind.FIN, p.titulo, p.direccionUbicacion, p.estado == "FINALIZADO", target) }
    }
    tasks.forEach { t ->
        val date = parse(t.dueDate) ?: return@forEach
        items += DueItem(date, DueKind.TAREA, t.title, projects.firstOrNull { it.id == t.projectId }?.titulo ?: "Servicio", t.completed,
            CatalogSelection(CatalogKind.PROJECTS, t.projectId))
    }
    return items.sortedWith(compareBy({ it.date }, { it.kind.ordinal }, { it.title }))
}

/**
 * Lo que merece un aviso: vencimientos (médicos, cursos, documentos, contratos) vencidos o próximos
 * y, si [includeTasks], tareas y finalizaciones previstas vencidas o próximas.
 */
fun HomeWorkspaceState.pendingWarnings(today: LocalDate, warnDays: Int, includeExpiries: Boolean, includeTasks: Boolean): List<DueItem> =
    dueItems().filter { item ->
        val status = item.status(today, warnDays)
        (status == DueStatus.VENCIDO || status == DueStatus.PROXIMO) &&
            ((item.kind.isExpiry && includeExpiries) || (!item.kind.isExpiry && item.kind != DueKind.INICIO && includeTasks))
    }

/** Recuento de vencimientos para la campana 🔔 y el calendario de Inicio (miembro 2). */
data class DueOverview(
    val expired: List<DueItem>,
    val upcoming: List<DueItem>,
    val valid: List<DueItem>,
    /** Documentación pendiente sin fecha: no vence, pero hay que entregarla. */
    val pendingWithoutDate: Int
) {
    /** Lo que merece la atención del administrador: vencidos, próximos y documentación sin fecha. */
    val bellCount: Int get() = expired.size + upcoming.size + pendingWithoutDate
    val hasUrgent: Boolean get() = expired.isNotEmpty()
}

/**
 * Resumen único de vencimientos con las mismas reglas que el calendario y las notificaciones,
 * para que todos los contadores de la app coincidan. [warnDays] viene de Ajustes.
 * Solo cuenta vencimientos (no tareas ni fechas de proyectos) y no incluye lo ya atendido.
 */
fun HomeWorkspaceState.dueOverview(today: LocalDate, warnDays: Int): DueOverview {
    val expiries = dueItems().filter { it.kind.isExpiry }
    fun with(status: DueStatus) = expiries.filter { it.status(today, warnDays) == status }
    return DueOverview(with(DueStatus.VENCIDO), with(DueStatus.PROXIMO), with(DueStatus.VIGENTE),
        obligations.count { !it.resolved && it.dueDate == null && it.kind == "DOCUMENTO" })
}
