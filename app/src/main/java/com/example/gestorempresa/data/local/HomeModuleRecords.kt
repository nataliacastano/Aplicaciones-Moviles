package com.example.gestorempresa.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** workerKind: EMPLEADO o SUBCONTRATADO. La zona laboral nunca se deduce de la residencia. */
@Entity(tableName = "home_worker_meta", primaryKeys = ["workerKind", "workerId", "workZone"])
data class HomeWorkerMeta(val workerKind: String, val workerId: Long, val workZone: String)

@Entity(tableName = "home_assignments", primaryKeys = ["workerKind", "workerId", "projectId"])
data class HomeAssignment(val workerKind: String, val workerId: Long, val projectId: Long)

/** kind: MEDICO, CURSO o DOCUMENTO. resolved archiva un aviso atendido, no certifica aptitud médica. */
@Entity(tableName = "home_obligations")
data class HomeObligation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val workerKind: String,
    val workerId: Long,
    val dueDate: String? = null,
    val resolved: Boolean = false
)

@Entity(tableName = "home_tasks")
data class HomeTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val projectId: Long,
    val dueDate: String,
    val completed: Boolean = false
)

/**
 * Archivo adjunto guardado en el almacenamiento interno de la app (fotos y documentos).
 * ownerKind: EMPLEADO, SUBCONTRATADO, PROYECTO, PROVEEDOR o MATERIAL.
 * type: FOTO o DOCUMENTO. category: CONTRATO, CERTIFICADO, CURSO, MEDICO, LABORAL, PRESUPUESTO, FACTURA, INFORME, OTRO.
 * expiresAt (AAAA-MM-DD) permite avisar antes de que caduque un contrato o certificado.
 */
@Entity(tableName = "home_attachments", indices = [Index(value = ["ownerKind", "ownerId"])])
data class HomeAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerKind: String,
    val ownerId: Long,
    val type: String,
    val category: String,
    val name: String,
    val path: String,
    val mimeType: String,
    val createdAt: String,
    val expiresAt: String? = null
)

/**
 * Ubicación en el mapa de una zona laboral (complementa a [HomeWorkerMeta], que guarda su nombre).
 * Un trabajador puede tener varias zonas, independientes de su dirección personal.
 */
@Entity(tableName = "home_zone_locations", primaryKeys = ["workerKind", "workerId", "workZone"])
data class HomeZoneLocation(
    val workerKind: String,
    val workerId: Long,
    val workZone: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
