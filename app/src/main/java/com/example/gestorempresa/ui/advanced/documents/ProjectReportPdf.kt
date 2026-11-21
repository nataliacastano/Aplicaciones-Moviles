package com.example.gestorempresa.ui.advanced.documents

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.gestorempresa.ui.advanced.stats.projectCosts
import com.example.gestorempresa.ui.home.HomeWorkspaceState
import java.io.OutputStream
import java.time.LocalDate

/**
 * Genera un informe PDF de un servicio o proyecto (tamaño A4) con sus datos,
 * equipo, horas, materiales, movimientos y el resumen de costes.
 * Se guarda como documento del proyecto para poder consultarlo o enviarlo al cliente.
 */
fun writeProjectReport(state: HomeWorkspaceState, projectId: Long, companyName: String, output: OutputStream) {
    val project = state.projects.first { it.id == projectId }
    val costs = state.projectCosts(projectId)
    val writer = PdfWriter()

    writer.title(companyName.ifBlank { "Informe" })
    writer.subtitle("Informe de ${project.tipo.lowercase()} · generado el ${LocalDate.now()}")
    writer.heading(project.titulo)
    writer.line("Cliente", project.clienteNombre.ifBlank { "Sin cliente asociado" })
    writer.line("Ubicación", project.direccionUbicacion)
    writer.line("Estado", project.estado)
    writer.line("Fecha de inicio", project.fechaInicio)
    writer.line("Finalización prevista", project.fechaFin ?: "Sin fecha prevista")

    writer.heading("Equipo asignado")
    val team = state.assignments.filter { it.projectId == projectId }
    if (team.isEmpty()) writer.text("Sin trabajadores asignados.")
    team.forEach { writer.text("• ${state.workerName(it.workerKind, it.workerId)} (${it.workerKind.lowercase()})") }

    writer.heading("Horas trabajadas")
    val hours = state.hours.filter { it.unidadTrabajoId == projectId }.sortedBy { it.fecha }
    if (hours.isEmpty()) writer.text("Todavía no hay horas registradas.")
    hours.forEach { h ->
        val who = state.workerName(if (h.empleadoId != null) "EMPLEADO" else "SUBCONTRATADO", h.empleadoId ?: h.subcontratadoId ?: 0)
        writer.line("${h.fecha} · $who", "${h.horasTrabajadas} h × ${money(h.costeHoraAplicado)} = ${money(h.horasTrabajadas * h.costeHoraAplicado)}")
    }

    writer.heading("Materiales utilizados")
    val uses = state.materialUses.filter { it.unidadTrabajoId == projectId }.sortedBy { it.fecha }
    if (uses.isEmpty()) writer.text("No se han registrado materiales.")
    uses.forEach { u ->
        val material = state.materials.firstOrNull { it.id == u.materialId }
        writer.line("${u.fecha} · ${material?.nombreMaterial ?: "Material"}",
            "${u.cantidad} ${material?.unidadMedida.orEmpty()} × ${money(u.precioUnitarioAplicado)} = ${money(u.cantidad * u.precioUnitarioAplicado)}")
    }

    writer.heading("Ingresos y gastos")
    val movements = state.movements.filter { it.projectId == projectId }.sortedBy { it.date }
    if (movements.isEmpty()) writer.text("No hay movimientos registrados.")
    movements.forEach { m -> writer.line("${m.date} · ${m.concept}", (if (m.income) "+" else "−") + money(m.amountCents / 100.0)) }

    writer.heading("Resumen económico")
    writer.line("Coste de personal", money(costs.personnel))
    writer.line("Coste de materiales", money(costs.materials))
    writer.line("Otros gastos", money(costs.otherExpenses))
    writer.line("Coste total", money(costs.total), bold = true)
    writer.line("Ingresos", money(costs.income))
    writer.line("Resultado", money(costs.income - costs.total), bold = true)

    writer.finish(output)
}

private fun money(value: Double) = "%.2f €".format(value)

/** Pequeño ayudante para escribir texto en páginas A4 y pasar de página automáticamente. */
private class PdfWriter {
    private val document = PdfDocument()
    private val width = 595
    private val height = 842
    private val margin = 48f
    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private var y = 0f
    private val body = Paint().apply { textSize = 11f; color = Color.rgb(30, 41, 59); isAntiAlias = true }
    private val bold = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val muted = Paint(body).apply { color = Color.rgb(100, 116, 139); textSize = 10f }
    private val accent = Paint().apply { color = Color.rgb(37, 99, 235) }

    init { newPage() }

    private fun newPage() {
        if (pageNumber > 0) finishPage()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        y = margin
    }

    private fun finishPage() {
        page.canvas.drawText("Página $pageNumber", width - margin - 50f, height - 24f, muted)
        document.finishPage(page)
    }

    private fun ensure(space: Float) { if (y + space > height - margin) newPage() }

    fun title(text: String) {
        ensure(40f)
        page.canvas.drawRect(0f, 0f, width.toFloat(), 6f, accent)
        page.canvas.drawText(text, margin, y + 14f, Paint(bold).apply { textSize = 20f })
        y += 28f
    }

    fun subtitle(text: String) { ensure(20f); page.canvas.drawText(text, margin, y + 4f, muted); y += 22f }

    fun heading(text: String) {
        ensure(36f)
        y += 10f
        page.canvas.drawText(text, margin, y + 12f, Paint(bold).apply { textSize = 14f; color = accent.color })
        y += 18f
        page.canvas.drawLine(margin, y, width - margin, y, Paint(muted).apply { strokeWidth = 0.5f })
        y += 10f
    }

    fun text(value: String) = wrap(value, body).forEach { line -> ensure(16f); page.canvas.drawText(line, margin, y + 10f, body); y += 15f }

    fun line(label: String, value: String, bold: Boolean = false) {
        ensure(16f)
        val paint = if (bold) this.bold else body
        val valueWidth = paint.measureText(value)
        val labelLines = wrap(label, paint, width - 2 * margin - valueWidth - 16f)
        page.canvas.drawText(value, width - margin - valueWidth, y + 10f, paint)
        labelLines.forEach { l -> ensure(16f); page.canvas.drawText(l, margin, y + 10f, paint); y += 15f }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float = width - 2 * margin): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.split(" ").forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) current = candidate
            else { lines += current; current = word }
        }
        if (current.isNotEmpty()) lines += current
        return lines.ifEmpty { listOf("") }
    }

    fun finish(output: OutputStream) {
        finishPage()
        try { document.writeTo(output) } finally { document.close() }
    }
}
