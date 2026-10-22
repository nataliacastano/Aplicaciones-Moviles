package com.example.gestorempresa.data.auth

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.entities.EmpresaEntity
import com.example.gestorempresa.data.local.entities.SesionEntity
import com.example.gestorempresa.data.local.entities.UsuarioEntity
import com.example.gestorempresa.domain.auth.*
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class RoomAuthRepository @Inject constructor(
    private val db: AppDatabase,
    private val hasher: PasswordHasher,
    private val clock: Clock
) : AuthRepository {
    private val dao = db.authDao()
    private val mutex = Mutex()

    // El reloj vuelve a evaluar la caducidad aunque no cambie ninguna fila de Room.
    override val sesion = combine(dao.observarSesion(), flow {
        while (true) { emit(clock.millis()); delay(1_000) }
    }) { row, ahora -> row?.takeIf { it.expiraEn > ahora }?.toDomain() }.distinctUntilChanged()

    override suspend fun registrar(datos: RegistroUsuario): ResultadoAuth = ejecutar {
        if (AuthValidator.validarRegistro(datos).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        val email = AuthValidator.normalizarEmail(datos.email)
        if (dao.buscarPorEmail(email) != null) return@ejecutar ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO)
        val digest = hasher.crear(datos.password)
        db.withTransaction {
            val empresaId = dao.insertarEmpresa(EmpresaEntity(nombre = datos.empresa.trim(), sector = datos.sector, creadaEn = clock.millis()))
            dao.insertarUsuario(UsuarioEntity(
                nombre = datos.nombre.trim(), apellidos = datos.apellidos.trim(), email = email,
                empresaId = empresaId, passwordHash = digest.hash, passwordSalt = digest.salt,
                passwordIterations = digest.iterations
            ))
        }
        // El reparto pide Registrarse -> Iniciar sesión. El alta no abre una sesión.
        ResultadoAuth.Correcto
    }

    override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth = ejecutar {
        if (AuthValidator.validarLogin(email, password).isNotEmpty()) return@ejecutar ResultadoAuth.Error(ErrorAuth.DATOS_INVALIDOS)
        val usuario = dao.buscarPorEmail(AuthValidator.normalizarEmail(email))
        if (usuario == null) {
            hasher.crear(password) // No se devuelve un error que revele si el correo está registrado.
            return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
        }
        val ahora = clock.millis()
        if (usuario.firebaseUid != null) return@ejecutar ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)
        if (usuario.bloqueoHasta > ahora) return@ejecutar ResultadoAuth.Error(ErrorAuth.BLOQUEO_TEMPORAL)
        val digest = PasswordDigest(usuario.passwordHash, usuario.passwordSalt, usuario.passwordIterations)
        if (!hasher.comprobar(password, digest)) {
            val previos = if (usuario.bloqueoHasta > 0 && usuario.bloqueoHasta <= ahora) 0 else usuario.intentosFallidos
            val intentos = previos + 1
            dao.actualizarIntentos(usuario.id, intentos, if (intentos >= 5) ahora + 30_000 else 0)
            return@ejecutar ResultadoAuth.Error(ErrorAuth.CREDENCIALES_INVALIDAS)
        }
        db.withTransaction {
            dao.actualizarIntentos(usuario.id, 0, 0)
            dao.guardarSesion(SesionEntity(usuarioId = usuario.id, expiraEn = ahora + DURACION_SESION))
        }
        ResultadoAuth.Correcto
    }

    override suspend fun cerrarSesion(): ResultadoAuth = ejecutar {
        dao.borrarSesion()
        ResultadoAuth.Correcto
    }

    override suspend fun recuperarPassword(email: String): ResultadoAuth =
        ResultadoAuth.Error(ErrorAuth.SERVICIO_NO_CONFIGURADO)

    private suspend fun ejecutar(block: suspend () -> ResultadoAuth): ResultadoAuth = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: SQLiteConstraintException) { ResultadoAuth.Error(ErrorAuth.EMAIL_EN_USO) }
            catch (_: Exception) { ResultadoAuth.Error(ErrorAuth.ALMACENAMIENTO) }
        }
    }

    companion object { const val DURACION_SESION = 7L * 24 * 60 * 60 * 1_000 }
}
