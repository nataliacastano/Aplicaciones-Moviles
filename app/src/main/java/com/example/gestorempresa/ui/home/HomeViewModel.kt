
package com.example.gestorempresa.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {

    // Datos de demostración porque aun no hay Base de Datos.
    // Se sustituirán por un repositorio real.
    private val _uiState = MutableStateFlow(
        HomeUiState(
            nombreAdministrador = "Carlos Ruiz",
            cargoAdministrador = "Director de Obra",

            empleados = 6,
            subcontratados = 0,
            serviciosActivos = 2,
            alertas = 4,

            totalObras = 5,
            obrasActivas = 2,
            trabajadoresEnCampo = 5,

            ingresos = 93500.0,
            gastos = 30300.0,

            listaAlertas = listOf(
                HomeAlert(
                    id = 1,
                    titulo = "Cemento CEM II/A",
                    descripcion = "Stock: 14 sacos (mínimo: 50)",
                    tipo = AlertType.STOCK_BAJO
                ),
                HomeAlert(
                    id = 2,
                    titulo = "Pintura blanca",
                    descripcion = "Stock: 6 botes (mínimo: 15)",
                    tipo = AlertType.STOCK_BAJO
                ),
                HomeAlert(
                    id = 3,
                    titulo = "Cable de cobre",
                    descripcion = "Stock: 3 rollos (mínimo: 8)",
                    tipo = AlertType.STOCK_BAJO
                ),
                HomeAlert(
                    id = 4,
                    titulo = "Certificación Chalet VistaMar",
                    descripcion = "Pendiente de cobro: 28.000 €",
                    tipo = AlertType.PAGO_PENDIENTE
                )
            )
        )
    )

    // Estado de solo lectura para la interfaz.
    val uiState: StateFlow<HomeUiState> =
        _uiState.asStateFlow()
}
