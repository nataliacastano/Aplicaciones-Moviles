package com.example.gestorempresa.domain.auth

import kotlinx.coroutines.flow.Flow

data class IdentidadRemota(val uid: String, val email: String)

class ErrorServicioAuth(val tipo: ErrorAuth) : Exception(tipo.name)

/** Las credenciales las verifica el proveedor; Room conserva el perfil empresarial. */
interface EmailAuthService {
    val configurado: Boolean
    val identidad: Flow<IdentidadRemota?>
    suspend fun registrar(email: String, password: String): IdentidadRemota
    suspend fun ingresar(email: String, password: String): IdentidadRemota
    suspend fun enviarRecuperacion(email: String)
    suspend fun actualizarSesion()
    suspend fun descartarAlta(uid: String)
    fun salir()
}
