package com.example.gestorempresa.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gestorempresa.domain.auth.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "", val password: String = "",
    val registro: RegistroUsuario = RegistroUsuario("", "", "", "", "", "", ""),
    val errores: Map<CampoAuth, String> = emptyMap(),
    val mensaje: String? = null, val ocupado: Boolean = false,
    val emailRecuperacion: String = "", val recuperacionSolicitada: Boolean = false
)

sealed interface AuthEvent { data class CuentaCreada(val email: String) : AuthEvent }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState(
        email = savedState["loginEmail"] ?: "",
        emailRecuperacion = savedState["emailRecuperacion"] ?: "",
        registro = RegistroUsuario(
            savedState["nombre"] ?: "", savedState["apellidos"] ?: "",
            savedState["registroEmail"] ?: "", "", "",
            savedState["empresa"] ?: "", savedState["sector"] ?: ""
        )
    ))
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()
    private val eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val eventos = eventChannel.receiveAsFlow()
    private var operation: Job? = null
    private val reintentos = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val sesion: StateFlow<EstadoSesion> = reintentos.flatMapLatest {
        repository.sesion.map<SesionUsuario?, EstadoSesion> { session ->
            if (session == null) EstadoSesion.SinSesion else EstadoSesion.Autenticada(session)
        }.onStart { emit(EstadoSesion.Cargando) }.catch { emit(EstadoSesion.Fallo) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoSesion.Cargando)

    fun editarLogin(email: String = _ui.value.email, password: String = _ui.value.password) {
        if (_ui.value.ocupado) return
        savedState["loginEmail"] = email
        _ui.update { it.copy(email = email, password = password, errores = emptyMap(), mensaje = null) }
    }

    fun editarRegistro(datos: RegistroUsuario) {
        if (_ui.value.ocupado) return
        savedState["nombre"] = datos.nombre
        savedState["apellidos"] = datos.apellidos
        savedState["registroEmail"] = datos.email
        savedState["empresa"] = datos.empresa
        savedState["sector"] = datos.sector
        _ui.update { it.copy(registro = datos, errores = emptyMap(), mensaje = null) }
    }

    fun iniciarSesion() {
        val form = _ui.value
        val errors = AuthValidator.validarLogin(form.email, form.password, maxLength = 4096)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errores = errors) }; return }
        lanzar {
            val result = repository.iniciarSesion(form.email, form.password)
            _ui.update { it.copy(password = "") }
            mostrarResultado(result)
        }
    }

    fun registrar() {
        val form = _ui.value.registro
        val errors = AuthValidator.validarRegistro(form)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errores = errors) }; return }
        lanzar {
            when (val result = repository.registrar(form)) {
                ResultadoAuth.Correcto -> {
                    val email = AuthValidator.normalizarEmail(form.email)
                    savedState["loginEmail"] = email
                    _ui.update { it.copy(email = email, registro = it.registro.copy(password = "", confirmarPassword = "")) }
                    eventChannel.send(AuthEvent.CuentaCreada(email))
                }
                is ResultadoAuth.Error -> mostrarResultado(result)
            }
        }
    }

    fun cerrarSesion() = lanzar {
        val result = repository.cerrarSesion()
        if (result == ResultadoAuth.Correcto) limpiarSecretos()
        mostrarResultado(result)
    }

    fun abrirRecuperacion() {
        limpiarSecretos()
        val email = _ui.value.email
        savedState["emailRecuperacion"] = email
        _ui.update { it.copy(emailRecuperacion = email, recuperacionSolicitada = false) }
    }

    fun editarEmailRecuperacion(email: String) {
        if (_ui.value.ocupado) return
        savedState["emailRecuperacion"] = email
        _ui.update { it.copy(emailRecuperacion = email, recuperacionSolicitada = false, errores = emptyMap(), mensaje = null) }
    }

    fun recuperarPassword() {
        if (_ui.value.ocupado || _ui.value.recuperacionSolicitada) return
        val email = _ui.value.emailRecuperacion
        val errors = AuthValidator.validarEmail(email)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errores = errors) }; return }
        lanzar {
            when (val result = repository.recuperarPassword(AuthValidator.normalizarEmail(email))) {
                ResultadoAuth.Correcto -> _ui.update { it.copy(recuperacionSolicitada = true, errores = emptyMap(), mensaje = null) }
                is ResultadoAuth.Error -> mostrarResultado(result)
            }
        }
    }

    fun volverDeRecuperacion() {
        val email = AuthValidator.normalizarEmail(_ui.value.emailRecuperacion)
        savedState["loginEmail"] = email
        limpiarSecretos()
        _ui.update { it.copy(email = email) }
    }

    fun limpiarSecretos() {
        _ui.update { it.copy(password = "", registro = it.registro.copy(password = "", confirmarPassword = ""), errores = emptyMap(), mensaje = null, recuperacionSolicitada = false) }
    }
    fun reintentarSesion() { reintentos.update { it + 1 } }

    private fun lanzar(block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        _ui.update { it.copy(ocupado = true, mensaje = null) }
        operation = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _ui.update { it.copy(mensaje = "No se pudo completar la operación. Inténtalo de nuevo.") } }
            finally { _ui.update { it.copy(ocupado = false) } }
        }
    }

    private fun mostrarResultado(result: ResultadoAuth) {
        if (result is ResultadoAuth.Error) _ui.update { state -> state.copy(mensaje = when (result.tipo) {
            ErrorAuth.EMAIL_EN_USO -> "Ya existe una cuenta con ese correo. Inicia sesión."
            ErrorAuth.CREDENCIALES_INVALIDAS -> "El correo o la contraseña no son correctos."
            ErrorAuth.BLOQUEO_TEMPORAL -> "Demasiados intentos. Espera 30 segundos antes de volver a probar."
            ErrorAuth.DATOS_INVALIDOS -> "Revisa los datos del formulario."
            ErrorAuth.ALMACENAMIENTO -> "No se han podido guardar o leer los datos. Inténtalo de nuevo."
            ErrorAuth.SERVICIO_NO_CONFIGURADO -> "El servicio de acceso por correo aún no está disponible. Contacta con el responsable de la aplicación."
            ErrorAuth.SIN_CONEXION -> "No se ha podido conectar. Comprueba tu conexión a Internet y vuelve a intentarlo."
            ErrorAuth.SERVICIO_NO_DISPONIBLE -> "El servicio de acceso no está disponible en este momento. Inténtalo más tarde."
            ErrorAuth.PERFIL_NO_DISPONIBLE -> "El correo y la contraseña son válidos, pero no encontramos el perfil de empresa guardado en este móvil. La app guarda ese perfil solo en el dispositivo donde se registró; si reinstalaste la app o cambiaste de móvil, no puede recuperarlo automáticamente."
            ErrorAuth.VINCULACION_NECESARIA -> "El correo ya tiene otra contraseña en el servicio de acceso. Contacta con el responsable para vincular tu cuenta anterior."
            ErrorAuth.DEMASIADAS_SOLICITUDES -> "Demasiadas solicitudes. Espera unos minutos antes de volver a intentarlo."
        }) }
    }
}
