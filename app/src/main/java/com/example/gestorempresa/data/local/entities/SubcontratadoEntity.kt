package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subcontratados")
data class SubcontratadoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombreComercial: String,
    val personaContacto: String,
    val cifDni: String,
    val telefono: String,
    val email: String,
    val oficio: String,              // Ej: "Fontanero", "Electricista"
    val tarifaHoraPorDefecto: Double,
    val fotoUrl: String? = null,
    val activo: Boolean = true
)