
package com.example.gestorempresa.ui.home

// Esto representa una alerta que aparecerá en Inicio.
data class HomeAlert(
    val id: Int,
    val titulo: String,
    val descripcion: String,
    val tipo: AlertType
)

// Estos son los tipos de alertas para distinguir su importancia.
enum class AlertType {
    STOCK_BAJO,
    PAGO_PENDIENTE,
    DOCUMENTACION,
    GENERAL
}

// Esto contiene toda la información necesaria para HomeScreen.
data class HomeUiState(

    // Datos generales del administrador.
    val nombreAdministrador: String = "",
    val cargoAdministrador: String = "",

    // Indicadores del panel principal.
    val empleados: Int = 0,
    val subcontratados: Int = 0,
    val serviciosActivos: Int = 0,
    val alertas: Int = 0,

    // Indicadores específicos del prototipo.
    val totalObras: Int = 0,
    val obrasActivas: Int = 0,
    val trabajadoresEnCampo: Int = 0,

    // Resumen económico.
    val ingresos: Double = 0.0,
    val gastos: Double = 0.0,

    // Avisos importantes.
    val listaAlertas: List<HomeAlert> = emptyList(),

    // Estado de la interfaz.
    val isLoading: Boolean = false,
    val error: String? = null
) {
    // Calcula la diferencia entre ingresos y gastos.
    val balance: Double
        get() = ingresos - gastos
}
