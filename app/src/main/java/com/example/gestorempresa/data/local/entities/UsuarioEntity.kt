package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usuarios",
    foreignKeys = [ForeignKey(entity = EmpresaEntity::class, parentColumns = ["id"], childColumns = ["empresaId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["email"], unique = true), Index(value = ["empresaId"]), Index(value = ["firebaseUid"], unique = true)]
)
data class UsuarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val apellidos: String,
    val email: String,
    val empresaId: Long,
    val rol: String = "ADMIN",
    val passwordHash: String,
    val passwordSalt: String,
    val passwordIterations: Int,
    val intentosFallidos: Int = 0,
    val bloqueoHasta: Long = 0,
    val firebaseUid: String? = null
)
