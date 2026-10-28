package com.example.gestorempresa.ui.employees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EmployeeListRoute(
    viewModel: EmployeeListViewModel,
    onBack: () -> Unit,
    onEmployeeClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EmployeeListScreen(state, viewModel::search, viewModel::filterTrade,
        viewModel::filterContract, viewModel::clearFilters, viewModel::retry,
        onBack, onEmployeeClick, modifier)
}

@Composable
fun EmployeeListScreen(
    state: EmployeeListUiState,
    onSearch: (String) -> Unit,
    onTradeChange: (String?) -> Unit,
    onContractChange: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onEmployeeClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = state.visibleEmployees
    Surface(modifier.fillMaxSize(), color = Color(0xFF020617)) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TextButton(onClick = onBack) { Text("Volver") } }
            item {
                Text("Empleados", style = MaterialTheme.typography.headlineMedium)
                Text("Empleados activos", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                OutlinedTextField(value = state.query, onValueChange = onSearch,
                    label = { Text("Buscar por nombre, apellidos u oficio") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item { EmployeeFilter("Oficio", state.trade, state.trades, onTradeChange) }
            item { EmployeeFilter("Tipo de contrato", state.contract, state.contracts, onContractChange) }
            if (state.query.isNotEmpty() || state.trade != null || state.contract != null) {
                item { TextButton(onClick = onClearFilters) { Text("Limpiar búsqueda y filtros") } }
            }
            when {
                state.loading -> item { CircularProgressIndicator() }
                state.error != null -> item {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text("Reintentar") }
                }
                state.employees.isEmpty() -> item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tu equipo empieza aquí", style = MaterialTheme.typography.titleMedium)
                            Text("Todavía no hay empleados activos. Vuelve a Inicio y pulsa + Alta empleado para añadir el primero.", color = Color(0xFF94A3B8))
                        }
                    }
                }
                visible.isEmpty() -> item { Text("No hay empleados que coincidan con la búsqueda y los filtros.") }
                else -> {
                    item { Text("${visible.size} empleados", style = MaterialTheme.typography.labelLarge) }
                    items(visible, key = { it.id }) { employee ->
                        Card(onClick = { onEmployeeClick(employee.id) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${employee.nombre} ${employee.apellidos}", style = MaterialTheme.typography.titleMedium)
                                Text(employee.oficio.ifBlank { "Oficio sin indicar" })
                                Text("${employee.tipoEmpleado} · Activo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeFilter(label: String, value: String?, options: List<String>, onChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value ?: "Todos"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todos") }, onClick = { onChange(null); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}
