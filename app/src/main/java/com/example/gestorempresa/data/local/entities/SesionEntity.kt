package com.example.gestorempresa.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sesion",
    foreignKeys = [ForeignKey(entity = UsuarioEntity::class, parentColumns = ["id"], childColumns = ["usuarioId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["usuarioId"])]
)
data class SesionEntity(@PrimaryKey val id: Int = 1, val usuarioId: Long, val expiraEn: Long)
