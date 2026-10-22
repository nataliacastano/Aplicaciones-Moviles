package com.example.gestorempresa.domain.auth

import java.util.Locale

object AuthValidator {
    val sectores = listOf("Construcción", "Limpieza", "Mantenimiento", "Reformas", "Servicios técnicos", "Peluquería", "Otro")
    private val emailPattern = Regex("^[A-Za-z0-9.!#%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$")

    fun normalizarEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    fun validarEmail(email: String): Map<CampoAuth, String> = buildMap {
        val normalized = normalizarEmail(email)
        if (normalized.length > 254 || !emailPattern.matches(normalized)) {
            put(CampoAuth.EMAIL, "Introduce un correo electrónico válido.")
        }
    }

    fun validarLogin(email: String, password: String, maxLength: Int = 128): Map<CampoAuth, String> = buildMap {
        putAll(validarEmail(email))
        if (password.isEmpty()) put(CampoAuth.PASSWORD, "Introduce tu contraseña.")
        else if (password.length > maxLength) put(CampoAuth.PASSWORD, "La contraseña admite hasta $maxLength caracteres.")
    }

    fun validarRegistro(datos: RegistroUsuario): Map<CampoAuth, String> = buildMap {
        putAll(validarLogin(datos.email, datos.password))
        if (datos.nombre.trim().length !in 2..60) put(CampoAuth.NOMBRE, "El nombre debe tener entre 2 y 60 caracteres.")
        if (datos.apellidos.trim().length !in 2..100) put(CampoAuth.APELLIDOS, "Los apellidos deben tener entre 2 y 100 caracteres.")
        if (datos.empresa.trim().length !in 2..120) put(CampoAuth.EMPRESA, "La empresa debe tener entre 2 y 120 caracteres.")
        if (datos.sector !in sectores) put(CampoAuth.SECTOR, "Selecciona un sector.")
        if (datos.password.length !in 12..128) put(CampoAuth.PASSWORD, "Utiliza entre 12 y 128 caracteres; puedes usar una frase.")
        if (datos.password != datos.confirmarPassword) put(CampoAuth.CONFIRMACION, "Las contraseñas no coinciden.")
    }
}
