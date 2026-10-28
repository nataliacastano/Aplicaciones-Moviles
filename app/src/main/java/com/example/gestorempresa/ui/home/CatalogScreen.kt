package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CatalogScreen(kind: CatalogKind, state: HomeWorkspaceState, vm: HomeWorkspaceViewModel,
    onBack: () -> Unit, onNavigate: (String) -> Unit, onSelect: (CatalogSelection) -> Unit) {
    val allFilters by vm.catalogFilters.collectAsStateWithLifecycle()
    val filter = allFilters[kind] ?: CatalogFilters()
    val rows = state.catalog(kind)
    val visible = filterCatalog(rows, filter)
    val saving by vm.saving.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val worker = kind == CatalogKind.EMPLOYEES || kind == CatalogKind.SUBCONTRACTORS
    val datesInvalid = (filter.fromDate.isNotEmpty() && !validDate(filter.fromDate)) ||
        (filter.toDate.isNotEmpty() && !validDate(filter.toDate)) ||
        (filter.fromDate.isNotEmpty() && filter.toDate.isNotEmpty() && filter.fromDate > filter.toDate)
    fun update(value: CatalogFilters) = vm.filter(kind, value)
    LazyColumn(modifier = Modifier.testTag("catalog-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Volver") } }
        item { Text(kind.title, style = MaterialTheme.typography.headlineMedium) }
        item { Button(onClick = { onNavigate(kind.newRoute) }) { Text("+ Añadir") } }
        if (worker) item { OutlinedButton(onClick = { onNavigate("assignment") }) { Text("Zona de trabajo y asignaciones") } }
        item { OutlinedTextField(filter.query, { update(filter.copy(query = it)) }, label = { Text("Buscar en ${kind.title.lowercase()}") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (worker) item { FilterChoice("Oficio", filter.trade, rows.map { it.trade }) { update(filter.copy(trade = it)) } }
        if (kind != CatalogKind.SUPPLIERS && kind != CatalogKind.SUBCONTRACTORS) item {
            FilterChoice(if (kind == CatalogKind.MATERIALS) "Unidad" else "Tipo", filter.type, rows.map { it.type }) { update(filter.copy(type = it)) }
        }
        if (worker || kind == CatalogKind.PROJECTS) {
            item { FilterChoice("Estado", filter.status, if (worker) listOf("ACTIVO", "INACTIVO") else listOf("ACTIVO", "PAUSADO", "FINALIZADO", "PENDIENTE")) { update(filter.copy(status = it)) } }
            item { FilterChoice(if (worker) "Zona de trabajo" else "Ubicación", filter.zone, rows.map { it.zone }) { update(filter.copy(zone = it)) } }
        }
        if (worker) item { IdFilter("Servicio / proyecto", filter.projectId, state.projects.map { it.id to it.titulo }) { update(filter.copy(projectId = it)) } }
        if (kind == CatalogKind.MATERIALS) item { IdFilter("Proveedor", filter.supplierId, state.suppliers.map { it.id to it.nombreEmpresa }) { update(filter.copy(supplierId = it)) } }
        if (kind == CatalogKind.PROJECTS) {
            item { EntryField("Inicio desde (AAAA-MM-DD)", filter.fromDate, { update(filter.copy(fromDate = it)) }, true) }
            item { EntryField("Inicio hasta (AAAA-MM-DD)", filter.toDate, { update(filter.copy(toDate = it)) }, true) }
            if (datesInvalid) item { Text("Revisa las fechas y el orden del intervalo.", color = MaterialTheme.colorScheme.error) }
        }
        if (filter != CatalogFilters()) item { TextButton(onClick = { update(CatalogFilters()) }) { Text("Limpiar búsqueda y filtros") } }
        item { Text("${visible.size} de ${rows.size} resultados", style = MaterialTheme.typography.labelLarge) }
        saveError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        when {
            state.loading -> item { CircularProgressIndicator() }
            state.error != null -> item { Text(state.error, color = MaterialTheme.colorScheme.error); Button(onClick = vm::retry) { Text("Reintentar") } }
            rows.isEmpty() -> item { EmptyCatalog("Todavía no hay registros. Pulsa + Añadir para crear el primero.") }
            visible.isEmpty() -> item { EmptyCatalog("No hay coincidencias. Prueba a limpiar la búsqueda y los filtros.") }
            else -> items(visible, key = { it.id }) { row ->
                Card(onClick = { onSelect(CatalogSelection(kind, row.id)) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(row.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (row.status.isNotBlank()) Text(row.status, color = if (row.status == "ACTIVO") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (worker) {
                            Text("Zona laboral: ${row.zone.ifBlank { "Sin indicar" }}")
                            Text("Asignado: ${state.projects.filter { it.id in row.projectIds }.joinToString { it.titulo }.ifBlank { "Sin asignación" }}")
                            TextButton(enabled = !saving, onClick = {
                                vm.save({ if (kind == CatalogKind.EMPLOYEES) setEmployeeActive(row.id, row.status != "ACTIVO") else setSubcontractorActive(row.id, row.status != "ACTIVO") }, {})
                            }) { Text(if (row.status == "ACTIVO") "Dar de baja" else "Reactivar") }
                        }
                        if (kind == CatalogKind.PROJECTS) {
                            Text("${row.startDate} → ${row.endDate ?: "Sin fin previsto"}")
                            ChoiceField("Cambiar estado", row.status, listOf("ACTIVO", "PAUSADO", "FINALIZADO"), !saving) {
                                status -> vm.save({ setProjectStatus(row.id, status) }, {})
                            }
                        }
                        TextButton(onClick = { onSelect(CatalogSelection(kind, row.id)) }) { Text("Abrir ficha") }
                    }
                }
            }
        }
    }
}

@Composable internal fun EmptyCatalog(message: String) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun FilterChoice(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    ChoiceField(label, value.ifBlank { "Todos" }, listOf("Todos") + options.filter { it.isNotBlank() }.distinct().sorted(), true) {
        onChange(if (it == "Todos") "" else it)
    }
}
@Composable private fun IdFilter(label: String, selected: Long?, options: List<Pair<Long, String>>, onChange: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${options.firstOrNull { it.first == selected }?.second ?: "Todos"}") }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onChange(null); expanded = false })
            options.forEach { (id, title) -> DropdownMenuItem(text = { Text("$title (#$id)") }, onClick = { onChange(id); expanded = false }) }
        }
    }
}
