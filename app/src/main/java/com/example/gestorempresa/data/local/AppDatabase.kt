package com.example.gestorempresa.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.gestorempresa.data.local.dao.EmpleadoDao
import com.example.gestorempresa.data.local.entities.*

@Database(
    entities = [EmpleadoEntity::class, UnidadTrabajoEntity::class,
                RegistroHorasEntity::class, SubcontratadoEntity::class,
                ProveedorEntity::class, MaterialEntity::class,
                ConsumoMaterialEntity::class
               ],
    version = 1,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {

    abstract fun empleadoDao(): EmpleadoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gestor_empresa_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}