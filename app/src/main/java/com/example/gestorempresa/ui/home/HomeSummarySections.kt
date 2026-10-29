package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.home.components.KpiCard

@Composable fun HomeSummarySections(state: HomeUiState, onSection: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard("Empleados", "${state.empleados}", "Activos", modifier = Modifier.weight(1f), onClick = { onSection("EMPLOYEES") })
            KpiCard("Subcontratados", "${state.subcontratados}", "Activos", modifier = Modifier.weight(1f), onClick = { onSection("SUBCONTRACTORS") })
        }
        Text("DOCUMENTACIÓN Y PLANIFICACIÓN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        val sections = listOf(
            Triple("Documentos pendientes", state.documentosPendientes, "DOCUMENTO"),
            Triple("Reconocimientos próximos o vencidos", state.medicosProximos, "MEDICO"),
            Triple("Cursos próximos o vencidos", state.cursosProximos, "CURSO"),
            Triple("Tareas pendientes", state.tareasPendientes, "tasks"),
            Triple("Servicios / proyectos pendientes", state.trabajosPendientes, "pending-projects")
        )
        sections.forEach { (label, count, route) ->
            OutlinedButton(onClick = { onSection(route) }, modifier = Modifier.fillMaxWidth()) { Text("$label: $count") }
        }
        if (state.proximasTareas.isNotEmpty()) {
            Text("PRÓXIMAS TAREAS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            state.proximasTareas.forEach { Text(it, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}
