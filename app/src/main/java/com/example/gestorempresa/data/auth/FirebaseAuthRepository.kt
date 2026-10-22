package com.example.gestorempresa.data.auth

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.*
import com.example.gestorempresa.domain.auth.*
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val db: AppDatabase,
    private val local: RoomAuthRepository,
    private val remote: EmailAuthService,
    private val hasher: PasswordHasher,
    private val clock: Clock
) : AuthRepository {
    private val dao = db.authDao()
    private val mutex = Mutex()
    override val sesion = combine(local.sesion, remote.identidad) { session, identity ->
        session?.takeIf { it.usuario.firebaseUid == null || it.usuario.firebaseUid == identity?.uid }
    }.onStart { remote.actualizarSesion() }.distinctUntilChanged()

    override suspend fun registrar(datos: RegistroUsuario): ResultadoAuth = ejecutar {
        if (AuthValidator.validarRegistro(datos).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        if (!remote.configurado) return@ejecutar ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        val email = AuthValidator.normalizarEmail(datos.email)
        if (dao.buscarPorEmail(email) != null) return@ejecutar ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO)
        val identity = remote.registrar(email, datos.password)
        try {
            db.withTransaction {
                val empresaId = dao.insertarEmpresa(EmpresaEntity(nombre = datos.empresa.trim(), sector = datos.sector, creadaEn = clock.millis()))
                dao.insertarUsuario(UsuarioEntity(nombre = datos.nombre.trim(), apellidos = datos.apellidos.trim(),
                    email = email, empresaId = empresaId, passwordHash = "", passwordSalt = "", passwordIterations = 0,
                    firebaseUid = identity.uid))
            }
        } catch (e: Exception) {
            withContext(NonCancellable) { runCatching { remote.descartarAlta(identity.uid) } }
            throw e
        } finally { remote.salir() }
        ResultadoAuth.Correcto
    }

    override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth = ejecutar {
        if (AuthValidator.validarLogin(email, password, maxLength = 4096).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        val normalized = AuthValidator.normalizarEmail(email)
        val profile = dao.buscarPorEmail(normalized)
        if (!remote.configurado) {
            return@ejecutar if (profile != null && profile.firebaseUid == null) local.iniciarSesion(email, password)
                else ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        }

        val identity = if (profile != null && profile.firebaseUid == null) {
            // Nunca se vincula una empresa antigua solo por la coincidencia del correo.
            val now = clock.millis()
            if (profile.bloqueoHasta > now) return@ejecutar ResultadoAuth.Error(ErrorAuth.BLOQUEO_TEMPORAL)
            if (!hasher.comprobar(password, PasswordDigest(profile.passwordHash, profile.passwordSalt, profile.passwordIterations))) {
                val previos = if (profile.bloqueoHasta > 0 && profile.bloqueoHasta <= now) 0 else profile.intentosFallidos
                val failures = previos + 1
                dao.actualizarIntentos(profile.id, failures, if (failures >= 5) now + 30_000 else 0)
                return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
            }
            try { remote.ingresar(normalized, password) }
            catch (e: ErrorServicioAuth) {
                if (e.tipo != ErrorAuth.CREDENCIALES_INVALIDAS) throw e
                try { remote.registrar(normalized, password) }
                catch (collision: ErrorServicioAuth) {
                    if (collision.tipo == ErrorAuth.EMAIL_EN_USO) throw ErrorServicioAuth(ErrorAuth.VINCULACION_NECESARIA)
                    throw collision
                }
            }
        } else remote.ingresar(normalized, password)

        if (profile == null) {
            remote.salir()
            return@ejecutar ResultadoAuth.Error(ErrorAuth.PERFIL_NO_DISPONIBLE)
        }
        if (identity.email != normalized || (profile.firebaseUid != null && profile.firebaseUid != identity.uid)) {
            remote.salir()
            return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
        }
        try {
            db.withTransaction {
                if (profile.firebaseUid == null) check(dao.vincularFirebase(profile.id, identity.uid) == 1)
                dao.actualizarIntentos(profile.id, 0, 0)
                dao.guardarSesion(SesionEntity(usuarioId = profile.id, expiraEn = clock.millis() + RoomAuthRepository.DURACION_SESION))
            }
        } catch (e: Exception) { remote.salir(); throw e }
        ResultadoAuth.Correcto
    }

    override suspend fun cerrarSesion(): ResultadoAuth = ejecutar {
        remote.salir()
        dao.borrarSesion()
        ResultadoAuth.Correcto
    }

    override suspend fun recuperarPassword(email: String): ResultadoAuth = ejecutar {
        if (AuthValidator.validarEmail(email).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        if (!remote.configurado) return@ejecutar ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        remote.enviarRecuperacion(AuthValidator.normalizarEmail(email))
        ResultadoAuth.Correcto
    }

    private suspend fun ejecutar(block: suspend () -> ResultadoAuth): ResultadoAuth = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: ErrorServicioAuth) { ResultadoAuth.Error(e.tipo) }
            catch (_: SQLiteConstraintException) { ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO) }
            catch (_: Exception) { ResultadoAuth.Error(ErrorAuth.ALMACENAMIENTO) }
        }
    }
}
