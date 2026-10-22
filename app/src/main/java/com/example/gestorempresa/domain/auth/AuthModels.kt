package com.example.gestorempresa.domain.auth

import kotlinx.coroutines.flow.Flow

data class Empresa(val id: Long, val nombre: String, val sector: String)

// El modelo que recibe la UI no contiene hashes, sales ni contraseñas.
data class Usuario(
    val id: Long,
    val nombre: String,
    val apellidos: String,
    val email: String,
    val empresaId: Long,
    val rol: String = "ADMIN",
    val firebaseUid: String? = null
)

data class SesionUsuario(val usuario: Usuario, val empresa: Empresa, val expiraEn: Long)

data class RegistroUsuario(
    val nombre: String,
    val apellidos: String,
    val email: String,
    val password: String,
    val confirmarPassword: String,
    val empresa: String,
    val sector: String
)

enum class CampoAuth { NOMBRE, APELLIDOS, EMAIL, PASSWORD, CONFIRMACION, EMPRESA, SECTOR }
enum class ErrorAuth {
    EMAIL_EN_USO, CREDENCIALES_INVALIDAS, BLOQUEO_TEMPORAL, DATOS_INVALIDOS, ALMACENAMIENTO,
    SERVICIO_NO_CONFIGURADO, SIN_CONEXION, SERVICIO_NO_DISPONIBLE, PERFIL_NO_DISPONIBLE,
    VINCULACION_NECESARIA, DEMASIADAS_SOLICITUDES
}

sealed interface ResultadoAuth {
    data object Correcto : ResultadoAuth
    data class Error(val tipo: ErrorAuth) : ResultadoAuth
}

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Autenticada(val sesion: SesionUsuario) : EstadoSesion
    data object Fallo : EstadoSesion
}

interface AuthRepository {
    val sesion: Flow<SesionUsuario?>
    suspend fun registrar(datos: RegistroUsuario): ResultadoAuth
    suspend fun iniciarSesion(email: String, password: String): ResultadoAuth
    suspend fun cerrarSesion(): ResultadoAuth
    suspend fun recuperarPassword(email: String): ResultadoAuth
}
