package com.example.gestorempresa.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Conserva usuarios, empresas y sesiones existentes al incorporar identidad remota. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE usuarios ADD COLUMN firebaseUid TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_usuarios_firebaseUid ON usuarios(firebaseUid)")
    }
}

/** Añade el número de afiliación del empleado conservando los registros anteriores. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE empleados ADD COLUMN numeroSeguridadSocial TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE empleados SET tipoEmpleado = 'COMPLETO' WHERE tipoEmpleado = 'FIJO'")
    }
}

/** Mantiene intactas las siete tablas de la versión 1 del equipo. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS empresas (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre TEXT NOT NULL, sector TEXT NOT NULL, creadaEn INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS usuarios (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre TEXT NOT NULL,
            apellidos TEXT NOT NULL, email TEXT NOT NULL, empresaId INTEGER NOT NULL,
            rol TEXT NOT NULL, passwordHash TEXT NOT NULL, passwordSalt TEXT NOT NULL,
            passwordIterations INTEGER NOT NULL, intentosFallidos INTEGER NOT NULL,
            bloqueoHasta INTEGER NOT NULL,
            FOREIGN KEY(empresaId) REFERENCES empresas(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_usuarios_email ON usuarios(email)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_usuarios_empresaId ON usuarios(empresaId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS sesion (
            id INTEGER NOT NULL PRIMARY KEY, usuarioId INTEGER NOT NULL, expiraEn INTEGER NOT NULL,
            FOREIGN KEY(usuarioId) REFERENCES usuarios(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sesion_usuarioId ON sesion(usuarioId)")
    }
}
