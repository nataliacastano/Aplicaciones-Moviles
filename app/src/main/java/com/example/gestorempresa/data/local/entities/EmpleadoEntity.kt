package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// esta clase representa la TABLA "empleados"
@Entity(tableName = "empleados")
data class EmpleadoEntity(
    // autoGenerate = true hace que el ID aumente solo al insertar
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nombre: String,
    val apellidos: String,
    val dni: String,
    val telefono: String,
    val email: String,
    val oficio: String,
    val tipoEmpleado: String,    //"FIJO", "PARCIAL"
    val salarioBase: Double,
    val lugarResidencia: String,
    val direccionPersonal: String,
    val fotoUrl: String? = null,
    val activo: Boolean = true   //dar de baja sin borrar los datos históricos
)