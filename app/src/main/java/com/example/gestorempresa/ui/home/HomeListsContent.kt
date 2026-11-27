package com.example.gestorempresa.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
import com.example.gestorempresa.data.media.AttachmentStorage
import com.example.gestorempresa.ui.advanced.AdvancedDetailFooter
import com.example.gestorempresa.ui.advanced.AdvancedDetailHeader
import com.example.gestorempresa.ui.advanced.dates.CalendarScreen
import com.example.gestorempresa.ui.advanced.dates.dueItems
import com.example.gestorempresa.ui.advanced.documents.AttachmentViewerScreen
import com.example.gestorempresa.ui.advanced.filesOwnedBy
import com.example.gestorempresa.ui.advanced.map.LocationPickerScreen
import com.example.gestorempresa.ui.advanced.map.ProjectsMapScreen
import com.example.gestorempresa.ui.advanced.stats.StatsScreen
import com.example.gestorempresa.domain.auth.SesionUsuario
import com.example.gestorempresa.ui.detail.DetailScreen
import com.example.gestorempresa.ui.detail.DetailViewModel
import com.example.gestorempresa.ui.navigation.BarraSuperior
import com.example.gestorempresa.ui.navigation.MenuLateral
import com.example.gestorempresa.ui.navigation.OpcionMenu
import com.example.gestorempresa.domain.profile.PerfilAdministrador
import kotlinx.coroutines.launch

/** El miembro 3 recibe tipo e id mediante onOpenDetails, sin duplicar sus pantallas. */
@Composable
fun HomeListsContent(session: SesionUsuario, modifier: Modifier = Modifier,
    onOpenDetails: ((CatalogSelection) -> Unit)? = null, warnDays: Int = 15, resetHomeSignal: Int = 0, onNotifyNow: () -> Unit = {}, notifyExpiries: Boolean = true, notifyTasks: Boolean = true,
    perfil: PerfilAdministrador? = null, menuUsuario: List<OpcionMenu> = emptyList(), onUserNavigation: ((String) -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val companyId = session.empresa.id
    val owner = remember(companyId, session.usuario.id) {
        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val dao = remember(companyId) { HomeDatabase.forCompany(context, companyId).homeDao() }
    // Fotos y documentos de la empresa (funciones avanzadas).
    val storage = remember(companyId) { AttachmentStorage(context, companyId) }
    LaunchedEffect(Unit) { AttachmentStorage.cleanTemporaryFiles(context) }
    // Plantillas de mensajes guardadas por la empresa.
    val templates = com.example.gestorempresa.ui.advanced.communication.rememberTemplateStore(companyId)
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
    val previousDestinations = rememberSaveable(companyId) { mutableStateListOf<String>() }
    var obligationKind by rememberSaveable(companyId) { mutableStateOf("Todos") }
    var editingEmployeeId by rememberSaveable(companyId) { mutableStateOf<Long?>(null) }
    var editingFromDetailId by rememberSaveable(companyId) { mutableStateOf<Long?>(null) }
    var editingCatalogItem by remember(companyId) { mutableStateOf<CatalogSelection?>(null) }
    var materialSupplierId by rememberSaveable(companyId) { mutableStateOf<Long?>(null) }
    var selectedDetail by remember(companyId) { mutableStateOf<CatalogSelection?>(null) }
    var deletingItem by remember(companyId) { mutableStateOf<CatalogSelection?>(null) }
    var notice by remember { mutableStateOf<HomeAlert?>(null) }
    var viewingAttachmentId by rememberSaveable(companyId) { mutableStateOf<Long?>(null) }
    var pickingLocationId by rememberSaveable(companyId) { mutableStateOf<Long?>(null) }
    // Conserva la posición de la ficha al volver del visor de documentos o del selector de ubicación.
    val detailListState = remember(selectedDetail) { androidx.compose.foundation.lazy.LazyListState() }
    val saving by workspace.saving.collectAsStateWithLifecycle()
    val saveError by workspace.saveError.collectAsStateWithLifecycle()
    fun navigate(route: String) {
        if (saving) return
        workspace.saveError.value = null
        val target = when (route) {
            "MEDICO", "CURSO", "DOCUMENTO" -> { obligationKind = route; "obligations" }
            "obligations" -> { obligationKind = "Todos"; route }
            "pending-projects" -> { workspace.filter(CatalogKind.PROJECTS, CatalogFilters(status = "PENDIENTE")); "PROJECTS" }
            else -> route
        }
        if (target == "home") {
            destination = "home"
            selectedDetail = null
            previousDestinations.clear()
        } else if (target != destination) {
            previousDestinations.add(destination)
            selectedDetail = null
            destination = target
        }
    }
    fun goBack() {
        if (viewingAttachmentId != null) { viewingAttachmentId = null; return }
        if (pickingLocationId != null) { pickingLocationId = null; return }
        if (selectedDetail != null) { selectedDetail = null; return }
        if (destination == "employee-edit" && editingFromDetailId != null) {
            selectedDetail = CatalogSelection(CatalogKind.EMPLOYEES, editingFromDetailId!!)
            editingFromDetailId = null
            destination = CatalogKind.EMPLOYEES.name
            return
        }
        if (destination == "record-edit" && editingCatalogItem != null) {
            selectedDetail = editingCatalogItem
            destination = editingCatalogItem!!.kind.name
            return
        }
        selectedDetail = null
        destination = if (previousDestinations.isNotEmpty()) previousDestinations.removeAt(previousDestinations.lastIndex) else "home"
    }
    // Menú lateral (☰): se abre desde la barra superior o deslizando desde el borde izquierdo.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(destination != "home" || selectedDetail != null) { goBack() }
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(pickingLocationId != null) { pickingLocationId = null }
    BackHandler(viewingAttachmentId != null) { viewingAttachmentId = null }
    val kind = CatalogKind.entries.firstOrNull { it.name == destination }
    val activity = destination in listOf("obligations", "tasks", "assignments")
    // La barra y el menú solo se muestran en las secciones principales, no en formularios ni detalles.
    val overlay = viewingAttachmentId != null || pickingLocationId != null
    val showMenu = !overlay && selectedDetail == null && (destination == "home" || kind != null || activity ||
        destination in listOf("messaging", "map", "stats", "calendar"))
    LaunchedEffect(resetHomeSignal) {
        if (resetHomeSignal > 0) {
            destination = "home"
            selectedDetail = null
            previousDestinations.clear()
            editingFromDetailId = null
        }
    }
    val alerts = remember(state, warnDays) { state.summary("", "", warnDays = warnDays).listaAlertas }
    HomeModuleTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showMenu && !saving,
            drawerContent = {
                MenuLateral(
                    titulo = session.empresa.nombre,
                    subtitulo = "${session.usuario.nombre} ${session.usuario.apellidos}",
                    opciones = homeMenuOptions(avisos = alerts.size, avisosUrgentes = alerts.any { it.urgente }) +
                        if (onUserNavigation != null) menuUsuario else emptyList(),
                    rutaSeleccionada = destination,
                    onElegir = { route ->
                        if (route.startsWith("user:") && onUserNavigation != null) {
                            scope.launch { drawerState.close(); onUserNavigation(route) }
                        } else { navigate(route); scope.launch { drawerState.close() } }
                    },
                    fotoRuta = perfil?.takeIf { it.usuarioId == session.usuario.id }?.fotoRuta,
                    iniciales = perfil?.takeIf { it.usuarioId == session.usuario.id }?.iniciales.orEmpty()
                )
            }
        ) {
            Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    if (showMenu) {
                        BarraSuperior(
                            titulo = homeMenuTitle(destination),
                            onAbrirMenu = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("module-menu")
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        when {
                            viewingAttachmentId != null -> {
                                val file = state.attachments.firstOrNull { it.id == viewingAttachmentId }
                                if (file == null) LaunchedEffect(Unit) { viewingAttachmentId = null }
                                else AttachmentViewerScreen(file, state.attachmentOwnerName(file), storage, onBack = { viewingAttachmentId = null },
                                    onDelete = { workspace.save({ deleteAttachment(file.id); storage.delete(file.path) }) { viewingAttachmentId = null } })
                            }
                            pickingLocationId != null -> {
                                val project = state.projects.firstOrNull { it.id == pickingLocationId }
                                if (project == null) LaunchedEffect(Unit) { pickingLocationId = null }
                                else LocationPickerScreen(project, workspace) { pickingLocationId = null }
                            }
                            selectedDetail != null -> DetailScreen(
                                content = remember(selectedDetail, state) { DetailViewModel(selectedDetail!!, state).content },
                                onBack = { goBack() },
                                onOpenRelated = { selectedDetail = it },
                                listState = detailListState,
                                header = { AdvancedDetailHeader(selectedDetail!!, state, workspace, storage, templates) },
                                footer = {
                                    AdvancedDetailFooter(selectedDetail!!, state, workspace, storage, session.empresa.nombre,
                                        onOpenAttachment = { viewingAttachmentId = it.id },
                                        onOpenMap = { selectedDetail = null; navigate("map") },
                                        onPickLocation = { pickingLocationId = it })
                                },
                                actions = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (selectedDetail?.kind == CatalogKind.EMPLOYEES) {
                                            Button(onClick = {
                                                editingEmployeeId = selectedDetail!!.id
                                                editingFromDetailId = selectedDetail!!.id
                                                selectedDetail = null
                                                destination = "employee-edit"
                                            }) { Text("Editar empleado") }
                                        } else {
                                            Button(onClick = {
                                                editingCatalogItem = selectedDetail
                                                destination = "record-edit"
                                                selectedDetail = null
                                            }) { Text("Editar") }
                                        }
                                        OutlinedButton(onClick = { deletingItem = selectedDetail }) { Text("Eliminar") }
                                        if (selectedDetail?.kind == CatalogKind.SUPPLIERS) {
                                            Button(onClick = {
                                                materialSupplierId = selectedDetail!!.id
                                                selectedDetail = null
                                                navigate("material-new")
                                            }) { Text("Añadir material a este proveedor") }
                                        }
                                    }
                                }
                            )
                            destination == "messaging" -> MessagingScreen(state, templates) { navigate("home") }
                            destination == "map" -> ProjectsMapScreen(state,
                                onOpenProject = { selectedDetail = CatalogSelection(CatalogKind.PROJECTS, it) },
                                onPickLocation = { pickingLocationId = it })
                            destination == "stats" -> StatsScreen(state)
                            destination == "calendar" -> CalendarScreen(state, warnDays, onOpen = { selectedDetail = it }, onNotifyNow = onNotifyNow, templates = templates, includeExpiries = notifyExpiries, includeTasks = notifyTasks)
                            kind != null -> CatalogScreen(kind, state, workspace, { navigate("home") }, ::navigate,
                                onSelect = { selection -> if (onOpenDetails != null) onOpenDetails(selection) else selectedDetail = selection },
                                onEditEmployee = { employeeId -> editingEmployeeId = employeeId; editingFromDetailId = null; navigate("employee-edit") })
                            activity -> ModuleActivityScreen(destination, state, workspace, obligationKind, { goBack() }, ::navigate, warnDays = warnDays)
                            destination in listOf("employee-new", "employee-edit", "project-new", "movement", "daily") ->
                                HomeEntryForm(destination, workspace, state, onBack = { goBack() },
                                    editingEmployee = if (destination == "employee-edit") state.employees.firstOrNull { it.id == editingEmployeeId } else null,
                                    storage = storage)
                            destination == "record-edit" && editingCatalogItem != null -> EditCatalogItemScreen(
                                editingCatalogItem!!, state, workspace,
                                onBack = { goBack() }
                            )
                            destination in listOf("subcontractor-new", "supplier-new", "material-new", "assignment", "obligation-new", "task-new") ->
                                ModuleRecordForm(destination, state, workspace, initialSupplierId = materialSupplierId) {
                                    if (destination == "material-new" && materialSupplierId != null) {
                                        val supplierId = materialSupplierId!!
                                        destination = "SUPPLIERS"
                                        selectedDetail = CatalogSelection(CatalogKind.SUPPLIERS, supplierId)
                                    } else goBack()
                                }
                            else -> HomeScreen(stateOverride = state.summary(session.usuario.nombre, session.empresa.nombre, warnDays = warnDays),
                                calendarItems = state.dueItems(),
                                onEmpleadosClick = { navigate("EMPLOYEES") }, onObrasClick = { navigate("PROJECTS") },
                                onAltaEmpleado = { navigate("employee-new") }, onNuevaObra = { navigate("project-new") },
                                onMovimiento = { navigate("movement") }, onParteDiario = { navigate("daily") },
                                onSectionClick = ::navigate, onRetry = workspace::retry, onOpenAllAlerts = { navigate("obligations") },
                                onAlertClick = { alert -> alert.destino?.let { navigate(it) } ?: run { notice = alert } })
                        }
                    }
                }
            }
        }
        notice?.let { alert ->
            AlertDialog(onDismissRequest = { notice = null }, title = { Text(alert.titulo) }, text = { Text(alert.descripcion) },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("Cerrar") } })
        }
        deletingItem?.let { selection ->
            val itemTitle = remember(selection, state) { DetailViewModel(selection, state).content.title }
            AlertDialog(
                onDismissRequest = { if (!saving) { deletingItem = null; workspace.saveError.value = null } },
                title = { Text("¿Eliminar $itemTitle?") },
                text = { Text(saveError ?: "Se eliminará este registro y los datos que dependan de él. Esta acción no se puede deshacer.",
                    color = if (saveError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(enabled = !saving, onClick = {
                        // Fotos y documentos que hay que borrar del móvil cuando se elimine el registro.
                        val files = state.filesOwnedBy(selection)
                        workspace.save({
                            when (selection.kind) {
                                CatalogKind.EMPLOYEES -> deleteEmployeeAndHistory(selection.id)
                                CatalogKind.SUBCONTRACTORS -> deleteSubcontractorAndHistory(selection.id)
                                CatalogKind.PROJECTS -> deleteProjectAndHistory(selection.id)
                                CatalogKind.SUPPLIERS -> deleteSupplierAndMaterials(selection.id)
                                CatalogKind.MATERIALS -> deleteMaterial(selection.id)
                            }
                            files.forEach { storage.delete(it) }
                        }) {
                            deletingItem = null
                            selectedDetail = null
                            destination = selection.kind.name
                        }
                    }) { Text(if (saving) "Eliminando…" else "Eliminar") }
                },
                dismissButton = { TextButton(enabled = !saving, onClick = { deletingItem = null; workspace.saveError.value = null }) { Text("Cancelar") } }
            )
        }
    }
}
