package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "empresas")
data class EmpresaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val sector: String,
    val creadaEn: Long
)
