package com.example.gestorempresa.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gestorempresa.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "movimientos")
data class HomeMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val concept: String,
    val amountCents: Long,
    val income: Boolean,
    val date: String
)

@Dao
interface HomeDao {
    @Query("SELECT * FROM empleados ORDER BY nombre, apellidos")
    fun employees(): Flow<List<EmpleadoEntity>>
    @Query("SELECT * FROM unidades_trabajo ORDER BY titulo")
    fun projects(): Flow<List<UnidadTrabajoEntity>>
    @Query("SELECT * FROM registros_horas ORDER BY fecha DESC, id DESC")
    fun hours(): Flow<List<RegistroHorasEntity>>
    @Query("SELECT * FROM movimientos ORDER BY date DESC, id DESC")
    fun movements(): Flow<List<HomeMovement>>
    @Insert suspend fun addEmployee(value: EmpleadoEntity)
    @Update suspend fun updateEmployee(value: EmpleadoEntity)
    @Insert suspend fun addProject(value: UnidadTrabajoEntity)
    @Update suspend fun updateProject(value: UnidadTrabajoEntity)
    @Insert suspend fun addHours(value: RegistroHorasEntity)
    @Insert suspend fun addMovement(value: HomeMovement)
    @Query("SELECT * FROM subcontratados ORDER BY nombreComercial")
    fun subcontractors(): Flow<List<SubcontratadoEntity>>
    @Query("SELECT * FROM proveedores ORDER BY nombreEmpresa")
    fun suppliers(): Flow<List<ProveedorEntity>>
    @Query("SELECT * FROM materiales ORDER BY nombreMaterial")
    fun materials(): Flow<List<MaterialEntity>>
    @Query("SELECT * FROM home_worker_meta")
    fun workerMeta(): Flow<List<HomeWorkerMeta>>
    @Query("SELECT * FROM home_assignments")
    fun assignments(): Flow<List<HomeAssignment>>
    @Query("SELECT * FROM home_obligations ORDER BY dueDate, id")
    fun obligations(): Flow<List<HomeObligation>>
    @Query("SELECT * FROM home_tasks ORDER BY dueDate, id")
    fun tasks(): Flow<List<HomeTask>>
    @Insert suspend fun addSubcontractor(value: SubcontratadoEntity)
    @Update suspend fun updateSubcontractor(value: SubcontratadoEntity)
    @Insert suspend fun addSupplier(value: ProveedorEntity)
    @Update suspend fun updateSupplier(value: ProveedorEntity)
    @Insert suspend fun addMaterial(value: MaterialEntity)
    @Update suspend fun updateMaterial(value: MaterialEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setWorkerMeta(value: HomeWorkerMeta)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun assign(value: HomeAssignment)
    @Query("DELETE FROM home_assignments WHERE workerKind = :kind AND workerId = :workerId AND projectId = :projectId")
    suspend fun unassign(kind: String, workerId: Long, projectId: Long)
    @Insert suspend fun addObligation(value: HomeObligation)
    @Insert suspend fun addTask(value: HomeTask)
    @Query("SELECT * FROM empleados WHERE id = :id") fun employee(id: Long): Flow<EmpleadoEntity?>
    @Query("SELECT * FROM subcontratados WHERE id = :id") fun subcontractor(id: Long): Flow<SubcontratadoEntity?>
    @Query("SELECT * FROM unidades_trabajo WHERE id = :id") fun project(id: Long): Flow<UnidadTrabajoEntity?>
    @Query("SELECT * FROM proveedores WHERE id = :id") fun supplier(id: Long): Flow<ProveedorEntity?>
    @Query("SELECT * FROM materiales WHERE id = :id") fun material(id: Long): Flow<MaterialEntity?>
    @Query("UPDATE empleados SET activo = :active WHERE id = :id")
    suspend fun setEmployeeActive(id: Long, active: Boolean)
    @Query("UPDATE subcontratados SET activo = :active WHERE id = :id")
    suspend fun setSubcontractorActive(id: Long, active: Boolean)
    @Query("UPDATE unidades_trabajo SET estado = :status WHERE id = :id")
    suspend fun setProjectStatus(id: Long, status: String)
    @Query("UPDATE home_tasks SET completed = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)
    @Query("UPDATE home_obligations SET resolved = :resolved WHERE id = :id")
    suspend fun setObligationResolved(id: Long, resolved: Boolean)

    @Query("DELETE FROM empleados WHERE id = :id") suspend fun deleteEmployee(id: Long)
    @Query("DELETE FROM registros_horas WHERE empleadoId = :id") suspend fun deleteEmployeeHours(id: Long)
    @Query("DELETE FROM home_worker_meta WHERE workerKind = 'EMPLEADO' AND workerId = :id") suspend fun deleteEmployeeMeta(id: Long)
    @Query("DELETE FROM home_assignments WHERE workerKind = 'EMPLEADO' AND workerId = :id") suspend fun deleteEmployeeAssignments(id: Long)
    @Query("DELETE FROM home_obligations WHERE workerKind = 'EMPLEADO' AND workerId = :id") suspend fun deleteEmployeeObligations(id: Long)
    @Transaction suspend fun deleteEmployeeAndHistory(id: Long) {
        deleteEmployeeHours(id); deleteEmployeeMeta(id); deleteEmployeeAssignments(id); deleteEmployeeObligations(id); deleteEmployee(id)
    }

    @Query("DELETE FROM subcontratados WHERE id = :id") suspend fun deleteSubcontractor(id: Long)
    @Query("DELETE FROM registros_horas WHERE subcontratadoId = :id") suspend fun deleteSubcontractorHours(id: Long)
    @Query("DELETE FROM home_worker_meta WHERE workerKind = 'SUBCONTRATADO' AND workerId = :id") suspend fun deleteSubcontractorMeta(id: Long)
    @Query("DELETE FROM home_assignments WHERE workerKind = 'SUBCONTRATADO' AND workerId = :id") suspend fun deleteSubcontractorAssignments(id: Long)
    @Query("DELETE FROM home_obligations WHERE workerKind = 'SUBCONTRATADO' AND workerId = :id") suspend fun deleteSubcontractorObligations(id: Long)
    @Transaction suspend fun deleteSubcontractorAndHistory(id: Long) {
        deleteSubcontractorHours(id); deleteSubcontractorMeta(id); deleteSubcontractorAssignments(id); deleteSubcontractorObligations(id); deleteSubcontractor(id)
    }

    @Query("DELETE FROM materiales WHERE proveedorId = :id") suspend fun deleteSupplierMaterials(id: Long)
    @Query("DELETE FROM proveedores WHERE id = :id") suspend fun deleteSupplier(id: Long)
    @Transaction suspend fun deleteSupplierAndMaterials(id: Long) { deleteSupplierMaterials(id); deleteSupplier(id) }
    @Query("DELETE FROM materiales WHERE id = :id") suspend fun deleteMaterial(id: Long)

    @Query("DELETE FROM registros_horas WHERE unidadTrabajoId = :id") suspend fun deleteProjectHours(id: Long)
    @Query("DELETE FROM movimientos WHERE projectId = :id") suspend fun deleteProjectMovements(id: Long)
    @Query("DELETE FROM home_assignments WHERE projectId = :id") suspend fun deleteProjectAssignments(id: Long)
    @Query("DELETE FROM home_tasks WHERE projectId = :id") suspend fun deleteProjectTasks(id: Long)
    @Query("DELETE FROM unidades_trabajo WHERE id = :id") suspend fun deleteProject(id: Long)
    @Transaction suspend fun deleteProjectAndHistory(id: Long) {
        deleteProjectHours(id); deleteProjectMovements(id); deleteProjectAssignments(id); deleteProjectTasks(id); deleteProject(id)
    }
}

/** Almacén del módulo 2 por empresa; no modifica las tablas de autenticación.
 * No importa registros del almacén anterior porque carecen de empresa propietaria.
 * Coordinar este contrato con los demás módulos antes de su integración de datos.
 */
@Database(entities = [EmpleadoEntity::class, UnidadTrabajoEntity::class,
    RegistroHorasEntity::class, HomeMovement::class, SubcontratadoEntity::class, ProveedorEntity::class,
    MaterialEntity::class, HomeWorkerMeta::class, HomeAssignment::class, HomeObligation::class,
    HomeTask::class], version = 3, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class HomeDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao
    companion object {
        private val instances = mutableMapOf<Long, HomeDatabase>()
        @Synchronized fun forCompany(context: Context, companyId: Long): HomeDatabase {
            require(companyId > 0)
            return instances.getOrPut(companyId) {
                Room.databaseBuilder(context.applicationContext, HomeDatabase::class.java,
                    "home_empresa_$companyId.db").addMigrations(HOME_MIGRATION_2_3).build()
            }
        }
    }
}

private val HOME_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE empleados ADD COLUMN numeroSeguridadSocial TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE empleados SET tipoEmpleado = 'COMPLETO' WHERE tipoEmpleado = 'FIJO'")
    }
}
