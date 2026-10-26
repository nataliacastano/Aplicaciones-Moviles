package com.example.gestorempresa.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gestorempresa.data.local.HomeDatabase
import com.example.gestorempresa.domain.auth.SesionUsuario

/** El miembro 3 recibe tipo e id mediante onOpenDetails, sin duplicar sus pantallas. */
@Composable
fun HomeListsContent(session: SesionUsuario, modifier: Modifier = Modifier,
    onOpenDetails: ((CatalogSelection) -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val companyId = session.empresa.id
    val owner = remember(companyId, session.usuario.id) {
        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val dao = remember(companyId) { HomeDatabase.forCompany(context, companyId).homeDao() }
    val factory = remember(dao) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == HomeViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(dao) as T
            }
        }
    }
    val workspace: HomeViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    val state by workspace.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable(companyId) { mutableStateOf("home") }
    var obligationKind by rememberSaveable(companyId) { mutableStateOf("Todos") }
    var notice by remember { mutableStateOf<HomeAlert?>(null) }
    val saving by workspace.saving.collectAsStateWithLifecycle()
    fun navigate(route: String) {
        if (saving) return
        workspace.saveError.value = null
        when (route) {
            "MEDICO", "CURSO", "DOCUMENTO" -> { obligationKind = route; destination = "obligations" }
            "obligations" -> { obligationKind = "Todos"; destination = route }
            "pending-projects" -> { workspace.filter(CatalogKind.PROJECTS, CatalogFilters(status = "PENDIENTE")); destination = "PROJECTS" }
            else -> destination = route
        }
    }
    BackHandler(destination != "home") { navigate("home") }
    val kind = CatalogKind.entries.firstOrNull { it.name == destination }
    val activity = destination in listOf("obligations", "tasks", "assignments")
    HomeModuleTheme {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                if (destination == "home" || kind != null || activity) {
                    val menu = listOf("home" to "Inicio") + CatalogKind.entries.map { it.name to it.title } +
                        listOf("obligations" to "Avisos", "tasks" to "Tareas", "assignments" to "Asignaciones")
                    LazyRow(modifier = Modifier.testTag("module-menu"), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(menu, key = { it.first }) { (route, title) ->
                            FilterChip(selected = route == destination, onClick = { navigate(route) }, label = { Text(title) }, enabled = !saving)
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    when {
                        kind != null -> CatalogScreen(kind, state, workspace, { navigate("home") }, ::navigate) { selection ->
                            if (onOpenDetails != null) onOpenDetails(selection)
                            else notice = HomeAlert(0, "Ficha pendiente de integración", "${selection.kind.title} · Identificador ${selection.id}. La ficha completa la conectará el miembro 3.", AlertType.GENERAL)
                        }
                        activity -> ModuleActivityScreen(destination, state, workspace, obligationKind, { navigate("home") }, ::navigate)
                        destination in listOf("employee-new", "project-new", "movement", "daily") ->
                            HomeEntryForm(destination, workspace, state) { destination = "home" }
                        destination in listOf("subcontractor-new", "supplier-new", "material-new", "assignment", "obligation-new", "task-new") ->
                            ModuleRecordForm(destination, state, workspace) { destination = "home" }
                        else -> HomeScreen(stateOverride = state.summary(session.usuario.nombre, session.empresa.nombre),
                            onEmpleadosClick = { navigate("EMPLOYEES") }, onObrasClick = { navigate("PROJECTS") },
                            onAltaEmpleado = { navigate("employee-new") }, onNuevaObra = { navigate("project-new") },
                            onMovimiento = { navigate("movement") }, onParteDiario = { navigate("daily") },
                            onSectionClick = ::navigate, onRetry = workspace::retry,
                            onAlertClick = { alert -> alert.destino?.let { navigate(it) } ?: run { notice = alert } })
                    }
                }
            }
        }
        notice?.let { alert ->
            AlertDialog(onDismissRequest = { notice = null }, title = { Text(alert.titulo) }, text = { Text(alert.descripcion) },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("Cerrar") } })
        }
    }
}
