package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materiales")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val proveedorId: Long,
    val nombreMaterial: String,
    val unidadMedida: String,        // Ej: "m2", "saco", "litro", "unidad"
    val precioUnitario: Double
)