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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.gestorempresa.ui.home.components.WelcomeCard
import com.example.gestorempresa.ui.home.components.KpiCard
import com.example.gestorempresa.ui.home.components.BalanceCard
import com.example.gestorempresa.ui.home.components.AlertsSection
import com.example.gestorempresa.ui.home.components.QuickActions

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(),
    onObrasClick: () -> Unit = {},
    onEmpleadosClick: () -> Unit = {},
    onNuevaObra: () -> Unit = {},
    onAltaEmpleado: () -> Unit = {},
    onMovimiento: () -> Unit = {},
    onParteDiario: () -> Unit = {},
    onAlertClick: (HomeAlert) -> Unit = {}
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF020617)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Text(
                text = "ObraControl",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
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
            }
        } else {

            item {
                WelcomeCard(
                    nombreAdministrador = uiState.nombreAdministrador,
                    cargoAdministrador = uiState.cargoAdministrador
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        titulo = "Obras activas",
                        valor = "${uiState.obrasActivas} / ${uiState.totalObras}",
                        descripcion = "En ejecución normal",
                        colorDescripcion = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        onClick = onObrasClick
                    )

                    KpiCard(
                        titulo = "En campo hoy",
                        valor = "${uiState.trabajadoresEnCampo} op.",
                        descripcion = "Personal en obra",
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
    name = "Inicio ObraControl",
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

