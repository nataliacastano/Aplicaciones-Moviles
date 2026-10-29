package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun ModuleActivityScreen(route: String, state: HomeWorkspaceState, vm: HomeWorkspaceViewModel,
    initialKind: String = "Todos", onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var query by rememberSaveable(route, initialKind) { mutableStateOf("") }
    var showArchived by rememberSaveable(route, initialKind) { mutableStateOf(false) }
    var kind by rememberSaveable(route, initialKind) { mutableStateOf(initialKind) }
    val saving by vm.saving.collectAsStateWithLifecycle()
    val error by vm.saveError.collectAsStateWithLifecycle()
    val today = LocalDate.now().toString()
    val title = when (route) { "tasks" -> "Tareas y trabajos pendientes"; "assignments" -> "Zonas laborales y asignaciones"; else -> "Documentación y vencimientos" }
    fun matches(text: String) = query.normalizedSearch().split(Regex("\\s+")).all { it in text.normalizedSearch() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Volver") } }
        item { Text(title, style = MaterialTheme.typography.headlineMedium) }
        item { Button(onClick = { onNavigate(when (route) { "tasks" -> "task-new"; "assignments" -> "assignment"; else -> "obligation-new" }) }) { Text("+ Añadir") } }
        item { OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, modifier = Modifier.fillMaxWidth()) }
        if (route != "assignments") item { FilterChip(showArchived, { showArchived = !showArchived }, label = { Text(if (showArchived) "Mostrar también atendidos" else "Solo pendientes") }) }
        if (route == "obligations") item { ChoiceField("Tipo", kind, listOf("Todos", "MEDICO", "CURSO", "DOCUMENTO"), true) { kind = it } }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.loading) item { CircularProgressIndicator() }
        else if (state.error != null) item { Text(state.error, color = MaterialTheme.colorScheme.error); Button(onClick = vm::retry) { Text("Reintentar") } }
        else when (route) {
            "tasks" -> {
                val tasks = state.tasks.filter { (showArchived || !it.completed) && matches("${it.title} ${it.dueDate} ${state.projects.firstOrNull { p -> p.id == it.projectId }?.titulo.orEmpty()}") }.sortedWith(compareBy({ it.completed }, { it.dueDate }))
                if (tasks.isEmpty()) item { EmptyCatalog("No hay tareas que coincidan con estos filtros.") }
                items(tasks, key = { it.id }) { task ->
                    ActivityCard(task.title, "${state.projects.firstOrNull { it.id == task.projectId }?.titulo.orEmpty()} · ${task.dueDate}",
                        if (task.completed) "Completada" else if (task.dueDate <= today) "Pendiente hoy o vencida" else "Programada",
                        !task.completed && task.dueDate <= today) {
                        TextButton(enabled = !saving, onClick = { vm.save({ setTaskCompleted(task.id, !task.completed) }, {}) }) { Text(if (task.completed) "Reabrir" else "Marcar completada") }
                    }
                }
            }
            "assignments" -> {
                item { Text("Zona laboral: dónde suele trabajar la persona. Asignación: servicio o proyecto concreto al que se incorpora.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val zones = state.workerMeta.filter { matches("${state.workerName(it.workerKind, it.workerId)} ${it.workZone}") }
                if (zones.isNotEmpty()) item { Text("ZONAS HABITUALES", style = MaterialTheme.typography.labelLarge) }
                items(zones, key = { "zone-${it.workerKind}-${it.workerId}" }) { meta ->
                    ActivityCard(state.workerName(meta.workerKind, meta.workerId), meta.workZone, "Zona habitual", false) { }
                }
                val assignments = state.assignments.filter { a -> matches("${state.workerName(a.workerKind, a.workerId)} ${state.projects.firstOrNull { it.id == a.projectId }?.titulo.orEmpty()}") }
                if (assignments.isNotEmpty()) item { Text("PROYECTOS ASIGNADOS", style = MaterialTheme.typography.labelLarge) }
                if (assignments.isEmpty() && zones.isEmpty()) item { EmptyCatalog("Todavía no hay zonas ni asignaciones. Añade una zona habitual y, si quieres, un proyecto para un empleado.") }
                items(assignments, key = { "${it.workerKind}-${it.workerId}-${it.projectId}" }) { a ->
                    ActivityCard(state.workerName(a.workerKind, a.workerId), state.projects.firstOrNull { it.id == a.projectId }?.titulo.orEmpty(), a.workerKind, false) {
                        TextButton(enabled = !saving, onClick = { vm.save({ unassign(a.workerKind, a.workerId, a.projectId) }, {}) }) { Text("Quitar asignación") }
                    }
                }
            }
            else -> {
                val obligations = state.obligations.filter { (showArchived || !it.resolved) && (kind == "Todos" || it.kind == kind) && matches("${it.title} ${state.workerName(it.workerKind, it.workerId)} ${it.dueDate.orEmpty()}") }
                    .sortedWith(compareBy({ it.resolved }, { it.dueDate ?: "9999-12-31" }))
                if (obligations.isEmpty()) item { EmptyCatalog("No hay documentos o vencimientos que coincidan con los filtros.") }
                items(obligations, key = { it.id }) { record ->
                    val urgent = !record.resolved && record.dueDate != null && record.dueDate <= today
                    val soon = record.dueDate != null && record.dueDate <= LocalDate.now().plusDays(30).toString()
                    val label = when { record.resolved -> "Atendido"; urgent -> "Vencido / urgente"; record.kind == "DOCUMENTO" -> "Documentación pendiente"; soon -> "Próximo a caducar"; else -> "Vigente" }
                    ActivityCard(record.title, "${state.workerName(record.workerKind, record.workerId)} · ${record.kind} · ${record.dueDate ?: "Sin fecha límite"}", label, urgent) {
                        TextButton(enabled = !saving, onClick = { vm.save({ setObligationResolved(record.id, !record.resolved) }, {}) }) { Text(if (record.resolved) "Reabrir aviso" else "Marcar atendido") }
                    }
                }
            }
        }
    }
}

@Composable private fun ActivityCard(title: String, subtitle: String, status: String, urgent: Boolean, actions: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, color = if (urgent) MaterialTheme.colorScheme.error else if (status == "Próximo a caducar" || status == "Documentación pendiente") Color(0xFFFBBF24) else MaterialTheme.colorScheme.secondary)
            actions()
        }
    }
}
