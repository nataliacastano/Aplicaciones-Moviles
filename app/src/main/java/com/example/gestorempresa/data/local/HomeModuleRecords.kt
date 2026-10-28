package com.example.gestorempresa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** workerKind: EMPLEADO o SUBCONTRATADO. La zona laboral nunca se deduce de la residencia. */
@Entity(tableName = "home_worker_meta", primaryKeys = ["workerKind", "workerId"])
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
