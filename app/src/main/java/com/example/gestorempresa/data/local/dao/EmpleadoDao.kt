package com.example.gestorempresa.data.local.dao

import androidx.room.*
import com.example.gestorempresa.data.local.entities.EmpleadoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmpleadoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmpleado(empleado: EmpleadoEntity)

    @Query("SELECT * FROM empleados WHERE activo = 1 ORDER BY nombre ASC")
    fun getEmpleadosActivos(): Flow<List<EmpleadoEntity>>

    @Query("UPDATE empleados SET activo = 0 WHERE id = :empleadoId")
    suspend fun desactivarEmpleado(empleadoId: Long)
}