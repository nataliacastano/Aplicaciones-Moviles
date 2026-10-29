package com.example.gestorempresa.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.gestorempresa.ui.home.components.KpiCard
import com.example.gestorempresa.ui.home.components.BalanceCard
import com.example.gestorempresa.ui.home.components.AlertsSection
import com.example.gestorempresa.ui.home.components.QuickActions

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel? = null,
    onObrasClick: () -> Unit = {},
    onEmpleadosClick: () -> Unit = {},
    onNuevaObra: () -> Unit = {},
    onAltaEmpleado: () -> Unit = {},
    onMovimiento: () -> Unit = {},
    onParteDiario: () -> Unit = {},
    onAlertClick: (HomeAlert) -> Unit = {},
    stateOverride: HomeUiState? = null,
    onSectionClick: (String) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val observedState = homeViewModel?.state?.collectAsStateWithLifecycle()?.value
    val uiState = stateOverride ?: observedState?.summary("", "") ?: HomeUiState()

    LazyColumn(
        modifier = modifier
            .testTag("home-list")
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Text(
                text = "Gestión de empresa",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (uiState.isLoading) {
            item {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error ?: "Error desconocido",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRetry) { Text("Reintentar") }
            }
        } else {

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        titulo = "Servicios activos",
                        valor = "${uiState.obrasActivas} / ${uiState.totalObras}",
                        descripcion = "En marcha",
                        colorDescripcion = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = onObrasClick
                    )

                    KpiCard(
                        titulo = "Personal hoy",
                        valor = "${uiState.trabajadoresEnCampo} op.",
                        descripcion = "Con parte registrado",
                        modifier = Modifier.weight(1f),
                        onClick = onEmpleadosClick
                    )
                }
            }

            item {
                BalanceCard(
                    ingresos = uiState.ingresos,
                    gastos = uiState.gastos
                )
            }

            item {
                QuickActions(
                    onNuevaObra = onNuevaObra,
                    onAltaEmpleado = onAltaEmpleado,
                    onMovimiento = onMovimiento,
                    onParteDiario = onParteDiario
                )
            }

            item { HomeSummarySections(uiState, onSectionClick) }

            item {
                AlertsSection(
                    alertas = uiState.listaAlertas,
                    onAlertClick = onAlertClick
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Inicio · Gestión de empresa",
    showBackground = true,
    widthDp = 393,
    heightDp = 852
)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen()
    }
}

