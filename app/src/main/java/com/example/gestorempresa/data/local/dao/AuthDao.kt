package com.example.gestorempresa.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.gestorempresa.data.local.entities.EmpresaEntity
import com.example.gestorempresa.data.local.entities.SesionEntity
import com.example.gestorempresa.data.local.entities.UsuarioEntity
import com.example.gestorempresa.domain.auth.Empresa
import com.example.gestorempresa.domain.auth.SesionUsuario
import com.example.gestorempresa.domain.auth.Usuario
import kotlinx.coroutines.flow.Flow

data class SesionRelacion(
    val usuarioId: Long, val nombre: String, val apellidos: String, val email: String,
    val empresaId: Long, val rol: String, val nombreEmpresa: String, val sector: String, val expiraEn: Long,
    val firebaseUid: String?
) {
    fun toDomain() = SesionUsuario(
        Usuario(usuarioId, nombre, apellidos, email, empresaId, rol, firebaseUid),
        Empresa(empresaId, nombreEmpresa, sector), expiraEn
    )
}

@Dao
interface AuthDao {
    @Insert suspend fun insertarEmpresa(empresa: EmpresaEntity): Long
    @Insert suspend fun insertarUsuario(usuario: UsuarioEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun guardarSesion(sesion: SesionEntity)
    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun buscarPorEmail(email: String): UsuarioEntity?
    @Query("DELETE FROM sesion") suspend fun borrarSesion()
    @Query("UPDATE usuarios SET intentosFallidos = :intentos, bloqueoHasta = :hasta WHERE id = :id")
    suspend fun actualizarIntentos(id: Long, intentos: Int, hasta: Long)
    @Query("UPDATE usuarios SET firebaseUid = :uid, passwordHash = '', passwordSalt = '', passwordIterations = 0, intentosFallidos = 0, bloqueoHasta = 0 WHERE id = :id AND firebaseUid IS NULL")
    suspend fun vincularFirebase(id: Long, uid: String): Int
    @Query("""
        SELECT u.id AS usuarioId, u.nombre, u.apellidos, u.email, u.empresaId, u.rol,
               e.nombre AS nombreEmpresa, e.sector, s.expiraEn, u.firebaseUid
        FROM sesion s JOIN usuarios u ON s.usuarioId = u.id
        JOIN empresas e ON u.empresaId = e.id WHERE s.id = 1
    """)
    fun observarSesion(): Flow<SesionRelacion?>
}
