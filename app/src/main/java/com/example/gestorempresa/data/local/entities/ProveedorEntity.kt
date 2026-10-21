package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proveedores")
data class ProveedorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombreEmpresa: String,
    val cif: String,
    val telefono: String,
    val email: String,
    val personaContacto: String
)