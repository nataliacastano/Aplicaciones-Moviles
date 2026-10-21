package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consumos_materiales")
data class ConsumoMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val unidadTrabajoId: Long,
    val materialId: Long,
    val cantidad: Double,
    val precioUnitarioAplicado: Double, // Foto del precio en la fecha del consumo
    val fecha: String
)