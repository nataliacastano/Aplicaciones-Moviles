package com.example.gestorempresa.data.auth

import android.content.Context
import com.example.gestorempresa.BuildConfig
import com.example.gestorempresa.domain.auth.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

@Singleton
class FirebaseEmailAuthService @Inject constructor(@ApplicationContext private val context: Context) : EmailAuthService {
    private val auth: FirebaseAuth? by lazy {
        val emulatorHost = if (BuildConfig.DEBUG) BuildConfig.AUTH_EMULATOR_HOST else ""
        val app = if (emulatorHost.isNotBlank()) {
            val name = "gestor-empresa-pruebas"
            FirebaseApp.getApps(context).firstOrNull { it.name == name }
                ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                    .setProjectId("demo-gestor-empresa")
                    .setApplicationId("1:1234567890:android:0123456789abcdef")
                    .setApiKey("AIzaSy" + "0".repeat(33)).build(), name)
        } else FirebaseApp.initializeApp(context)
        app?.let {
            FirebaseAuth.getInstance(it).apply {
                setLanguageCode("es")
                if (emulatorHost.isNotBlank()) useEmulator(emulatorHost, 9099)
            }
        }
    }
    override val configurado: Boolean get() = auth != null

    override val identidad = callbackFlow<IdentidadRemota?> {
        val sdk = auth
        if (sdk == null) {
            trySend(null)
            awaitClose { }
        } else {
            val listener = FirebaseAuth.IdTokenListener { current ->
                trySend(current.currentUser?.let { IdentidadRemota(it.uid, it.email.orEmpty()) })
            }
            sdk.addIdTokenListener(listener)
            awaitClose { sdk.removeIdTokenListener(listener) }
        }
    }

    override suspend fun registrar(email: String, password: String): IdentidadRemota = llamar {
        val user = servicio().createUserWithEmailAndPassword(email, password).esperar().user
            ?: throw ErrorServicioAuth(ErrorAuth.SERVICIO_NO_DISPONIBLE)
        IdentidadRemota(user.uid, user.email.orEmpty())
    }

    override suspend fun ingresar(email: String, password: String): IdentidadRemota = llamar {
        val user = servicio().signInWithEmailAndPassword(email, password).esperar().user
            ?: throw ErrorServicioAuth(ErrorAuth.CREDENCIALES_INVALIDAS)
        IdentidadRemota(user.uid, user.email.orEmpty())
    }

    override suspend fun enviarRecuperacion(email: String) {
        try { llamar { servicio().sendPasswordResetEmail(email).esperar() } }
        catch (e: ErrorServicioAuth) {
            // La respuesta no revela si el correo existe, incluso en proyectos sin protección de enumeración.
            if (e.tipo != ErrorAuth.CREDENCIALES_INVALIDAS) throw e
        }
    }

    override suspend fun actualizarSesion() {
        val user = auth?.currentUser ?: return
        try {
            withTimeout(15_000) { user.reload().esperar(); user.getIdToken(true).esperar() }
        } catch (_: FirebaseAuthInvalidUserException) { salir() }
        catch (_: FirebaseAuthInvalidCredentialsException) { salir() }
        catch (_: FirebaseNetworkException) { /* Conserva la sesión local válida durante una desconexión. */ }
        catch (_: TimeoutCancellationException) { }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { salir() }
    }

    override suspend fun descartarAlta(uid: String) {
        auth?.currentUser?.takeIf { it.uid == uid }?.delete()?.esperar()
    }
    override fun salir() { auth?.signOut() }
    private fun servicio() = auth ?: throw ErrorServicioAuth(ErrorAuth.SERVICIO_NO_CONFIGURADO)

    private suspend fun <T> llamar(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: ErrorServicioAuth) { throw e }
    catch (_: FirebaseNetworkException) { throw ErrorServicioAuth(ErrorAuth.SIN_CONEXION) }
    catch (_: FirebaseTooManyRequestsException) { throw ErrorServicioAuth(ErrorAuth.DEMASIADAS_SOLICITUDES) }
    catch (_: FirebaseAuthUserCollisionException) { throw ErrorServicioAuth(ErrorAuth.EMAIL_EN_USO) }
    catch (_: FirebaseAuthWeakPasswordException) { throw ErrorServicioAuth(ErrorAuth.DATOS_INVALIDOS) }
    catch (_: FirebaseAuthInvalidCredentialsException) { throw ErrorServicioAuth(ErrorAuth.CREDENCIALES_INVALIDAS) }
    catch (_: FirebaseAuthInvalidUserException) { throw ErrorServicioAuth(ErrorAuth.CREDENCIALES_INVALIDAS) }
    catch (e: FirebaseAuthException) {
        throw ErrorServicioAuth(if (e.errorCode in setOf("ERROR_OPERATION_NOT_ALLOWED", "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED"))
            ErrorAuth.SERVICIO_NO_CONFIGURADO else ErrorAuth.SERVICIO_NO_DISPONIBLE)
    }
    catch (_: Exception) { throw ErrorServicioAuth(ErrorAuth.SERVICIO_NO_DISPONIBLE) }

    private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isActive) {
                if (task.isSuccessful) continuation.resume(task.result)
                else continuation.resumeWithException(task.exception ?: IllegalStateException("Operación de autenticación fallida"))
            }
        }
    }
}
