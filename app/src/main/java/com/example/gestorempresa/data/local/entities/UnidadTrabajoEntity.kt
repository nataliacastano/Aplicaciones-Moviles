package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unidades_trabajo")
data class UnidadTrabajoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val titulo: String,
    val tipo: String,
    val clienteNombre: String,
    val direccionUbicacion: String,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val estado: String = "ACTIVO",  // "ACTIVO", "PAUSADO", "FINALIZADO"
    val fechaInicio: String,
    val fechaFin: String? = null
)