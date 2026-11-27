package com.example.gestorempresa.data.profile

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.data.local.MIGRATION_4_5
import com.example.gestorempresa.data.local.entities.EmpresaEntity
import com.example.gestorempresa.data.local.entities.UsuarioEntity
import com.example.gestorempresa.data.settings.RoomSettingsRepository
import com.example.gestorempresa.domain.profile.DatosPerfil
import com.example.gestorempresa.domain.settings.AjustesApp
import com.example.gestorempresa.domain.settings.TemaApp
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProfileRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val perfiles = RoomProfileRepository(db, FotoPerfilStorage(context), CuentasEnMemoria())
    private val ajustes = RoomSettingsRepository(db)

    @After fun cerrar() = db.close()

    /** Crea un administrador como lo haría el Registro y devuelve (usuarioId, empresaId). */
    private suspend fun registrarAdmin(): Pair<Long, Long> {
        val empresaId = db.authDao().insertarEmpresa(EmpresaEntity(nombre = "Construcciones Murcia", sector = "Construcción", creadaEn = 0))
        val usuarioId = db.authDao().insertarUsuario(UsuarioEntity(nombre = "Juan", apellidos = "Pérez", email = "juan@empresa.es",
            empresaId = empresaId, passwordHash = "", passwordSalt = "", passwordIterations = 0))
        return usuarioId to empresaId
    }

    @Test fun usuarioRecienRegistradoTienePerfilVacioYAjustesPorDefecto() = runBlocking {
        val (usuarioId, _) = registrarAdmin()
        val perfil = perfiles.observarPerfil(usuarioId).first()!!
        assertEquals("Juan Pérez", perfil.nombreCompleto)
        assertEquals("JP", perfil.iniciales)
        assertEquals("", perfil.telefono)
        assertNull(perfil.fotoRuta)
        assertEquals(AjustesApp(), ajustes.observarAjustes(usuarioId).first())
    }

    @Test fun guardarPerfilActualizaUsuarioEmpresaYDatosPersonales() = runBlocking {
        val (usuarioId, empresaId) = registrarAdmin()
        perfiles.guardarPerfil(usuarioId, empresaId, DatosPerfil("Juana", "Pérez Ruiz", "600123456", "12345678z", "Gerente", "Limpiezas Murcia", "Limpieza"))
        val perfil = perfiles.observarPerfil(usuarioId).first()!!
        assertEquals("Juana Pérez Ruiz", perfil.nombreCompleto)
        assertEquals("12345678Z", perfil.dni)
        assertEquals("Gerente", perfil.cargo)
        assertEquals("Limpiezas Murcia", perfil.empresa.nombre)
        assertEquals("Limpieza", perfil.empresa.sector)
    }

    @Test fun cambiarFotoCopiaLaImagenYBorrarlaVuelveAlAvatar() = runBlocking {
        val (usuarioId, _) = registrarAdmin()
        // Simula la imagen que devolvería la galería del móvil.
        val imagen = Uri.parse("content://galeria/foto.jpg")
        shadowOf(context.contentResolver).registerInputStream(imagen, ByteArrayInputStream(byteArrayOf(1, 2, 3)))

        perfiles.cambiarFoto(usuarioId, imagen.toString())
        val ruta = perfiles.observarPerfil(usuarioId).first()!!.fotoRuta!!
        assertArrayEquals(byteArrayOf(1, 2, 3), File(ruta).readBytes())

        perfiles.eliminarFoto(usuarioId)
        assertNull(perfiles.observarPerfil(usuarioId).first()!!.fotoRuta)
        assertFalse(File(ruta).exists())
    }

    @Test fun guardaYRecuperaAjustes() = runBlocking {
        val (usuarioId, _) = registrarAdmin()
        val nuevos = AjustesApp(avisosVencimientos = false, diasAntelacion = 30, avisosTareas = false, tema = TemaApp.OSCURO)
        ajustes.guardarAjustes(usuarioId, nuevos)
        assertEquals(nuevos, ajustes.observarAjustes(usuarioId).first())
    }

    @Test fun migracionCuatroACincoConservaUsuarios() = runBlocking {
        // Crea una base de datos con el esquema de la versión 4 (antes de Perfil y Ajustes).
        val nombre = "migracion_perfil_test.db"
        context.deleteDatabase(nombre)
        val schema = javaClass.classLoader!!.getResourceAsStream("com.example.gestorempresa.data.local.AppDatabase/4.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(nombre, Context.MODE_PRIVATE, null).use { old ->
            val tablas = schema.getJSONArray("entities")
            for (i in 0 until tablas.length()) {
                val tabla = tablas.getJSONObject(i)
                old.execSQL(tabla.getString("createSql").replace("\${TABLE_NAME}", tabla.getString("tableName")))
                val indices = tabla.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tabla.getString("tableName")))
                }
            }
            val consultas = schema.getJSONArray("setupQueries")
            for (i in 0 until consultas.length()) old.execSQL(consultas.getString(i))
            old.execSQL("INSERT INTO empresas (id,nombre,sector,creadaEn) VALUES (1,'Empresa conservada','Reformas',0)")
            old.execSQL("INSERT INTO usuarios (id,nombre,apellidos,email,empresaId,rol,passwordHash,passwordSalt,passwordIterations,intentosFallidos,bloqueoHasta,firebaseUid) VALUES (5,'Ana','García','ana@empresa.es',1,'ADMIN','','',0,0,0,NULL)")
            old.version = 4
        }

        // Room comprueba al abrir que las tablas creadas por la migración coinciden con las entidades.
        val migrada = Room.databaseBuilder(context, AppDatabase::class.java, nombre).addMigrations(MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            val perfil = RoomProfileRepository(migrada, FotoPerfilStorage(context), CuentasEnMemoria()).observarPerfil(5).first()!!
            assertEquals("Ana García", perfil.nombreCompleto)
            assertEquals("Empresa conservada", perfil.empresa.nombre)
        } finally { migrada.close() }
    }
}

/** Sustituto en memoria del perfil compartido en Firestore: los tests no usan la red. */
private class CuentasEnMemoria : com.example.gestorempresa.domain.auth.PerfilCuentaService {
    private val perfiles = mutableMapOf<String, com.example.gestorempresa.domain.auth.PerfilCuentaRemoto>()
    override suspend fun obtener(uid: String) = perfiles[uid]
    override suspend fun crearSiFalta(perfil: com.example.gestorempresa.domain.auth.PerfilCuentaRemoto) =
        perfiles.getOrPut(perfil.uid) { perfil }
    override suspend fun actualizar(perfil: com.example.gestorempresa.domain.auth.PerfilCuentaRemoto) { perfiles[perfil.uid] = perfil }
}
