package com.example.gestorempresa.ui.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.data.local.entities.EmpleadoEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

data class EmployeeListUiState(
    val employees: List<EmpleadoEntity> = emptyList(),
    val query: String = "",
    val trade: String? = null,
    val contract: String? = null,
    val loading: Boolean = true,
    val error: String? = null
) {
    val trades: List<String> get() = employees.map { it.oficio }.filter { it.isNotBlank() }.distinct().sorted()
    val contracts: List<String> get() = employees.map { it.tipoEmpleado }.filter { it.isNotBlank() }.distinct().sorted()
    val visibleEmployees: List<EmpleadoEntity> get() {
        val terms = query.searchKey().split(Regex("\\s+")).filter { it.isNotBlank() }
        return employees.filter { employee ->
            val searchable = "${employee.nombre} ${employee.apellidos} ${employee.oficio}".searchKey()
            employee.activo && terms.all { it in searchable } &&
                (trade == null || employee.oficio == trade) &&
                (contract == null || employee.tipoEmpleado == contract)
        }.sortedWith(compareBy({ it.nombre.searchKey() }, { it.apellidos.searchKey() }, { it.id }))
    }
}

private fun String.searchKey(): String = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

/** Fuente observable. El aislamiento por empresa requiere ampliar el esquema compartido. */
class EmployeeListViewModel(
    private val observeEmployees: () -> Flow<List<EmpleadoEntity>>
) : ViewModel() {
    private val mutableState = MutableStateFlow(EmployeeListUiState())
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null

    init { retry() }

    fun search(value: String) { mutableState.update { it.copy(query = value) } }
    fun filterTrade(value: String?) { mutableState.update { it.copy(trade = value) } }
    fun filterContract(value: String?) { mutableState.update { it.copy(contract = value) } }
    fun clearFilters() { mutableState.update { it.copy(query = "", trade = null, contract = null) } }

    fun retry() {
        observation?.cancel()
        observation = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                observeEmployees().collect { employees ->
                    mutableState.update { it.copy(employees = employees, loading = false, error = null) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(employees = emptyList(), loading = false, error = "No se han podido cargar los empleados.") }
            }
        }
    }
}
