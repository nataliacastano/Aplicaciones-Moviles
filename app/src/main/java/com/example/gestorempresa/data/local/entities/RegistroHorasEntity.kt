package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registros_horas")
data class RegistroHorasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val unidadTrabajoId: Long,      // ID del proyecto/servicio
    val empleadoId: Long? = null,    // ID si es empleado propio
    val subcontratadoId: Long? = null, // ID si es subcontratado
    val fecha: String,              // Formato "YYYY-MM-DD"
    val horasTrabajadas: Double,
    val costeHoraAplicado: Double   // Foto de la tarifa en la fecha del registro
)