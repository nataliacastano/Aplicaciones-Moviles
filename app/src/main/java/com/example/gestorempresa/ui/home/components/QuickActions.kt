package com.example.gestorempresa.ui.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun QuickActions(
    onNuevaObra: () -> Unit,
    onAltaEmpleado: () -> Unit,
    onMovimiento: () -> Unit,
    onParteDiario: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ACCIONES RÁPIDAS",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFCBD5E1)
        )

        Row(
            modifier = Modifier.horizontalScroll(
                rememberScrollState()
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionButton(
                texto = "+ Nueva obra",
                color = Color(0xFFFB923C),
                onClick = onNuevaObra
            )

            QuickActionButton(
                texto = "+ Alta empleado",
                color = Color(0xFF60A5FA),
                onClick = onAltaEmpleado
            )

            QuickActionButton(
                texto = "Gasto / Ingreso",
                color = Color(0xFF34D399),
                onClick = onMovimiento
            )

            QuickActionButton(
                texto = "Parte diario",
                color = Color(0xFFC084FC),
                onClick = onParteDiario
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    texto: String,
    color: Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = texto,
            color = color
        )
    }
}

