
package com.example.gestorempresa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.auth.AuthViewModel
import com.example.gestorempresa.ui.home.HomeListsContent
import com.example.gestorempresa.ui.navigation.AuthRoot
import com.example.gestorempresa.ui.theme.GestorEmpresaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GestorEmpresaTheme {
                AuthRoot(
                    viewModel = authViewModel,
                    contenidoAutenticado = { session, onLogout ->
                        Column(
                                modifier = Modifier.fillMaxSize().safeDrawingPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onLogout) {
                                    Text("Cerrar sesión")
                                }
                            }

                            HomeListsContent(
                                session = session,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                )
            }
        }
    }
}
