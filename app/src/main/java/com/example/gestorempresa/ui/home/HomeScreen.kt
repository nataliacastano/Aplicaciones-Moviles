package com.example.gestorempresa.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class HomeSummary(
    val title: String,
    val value: String
)

private val sampleSummaries = listOf(
    HomeSummary("Empleados", "12"),
    HomeSummary("Subcontratados", "4"),
    HomeSummary("Servicios activos", "6"),
    HomeSummary("Alertas", "3")
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Panel de administración",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Resumen general de la empresa",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleSummaries) { summary ->
                SummaryCard(summary)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: HomeSummary) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = summary.value,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
