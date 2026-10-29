package com.example.gestorempresa.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.home.AlertType
import com.example.gestorempresa.ui.home.HomeAlert

@Composable
fun AlertsSection(
    alertas: List<HomeAlert>,
    modifier: Modifier = Modifier,
    onAlertClick: (HomeAlert) -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "AVISOS Y VENCIMIENTOS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "${alertas.size} avisos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (alertas.isEmpty()) {
            Text(
                text = "No hay avisos pendientes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            alertas.forEach { alerta ->
                AlertCard(
                    alerta = alerta,
                    onClick = { onAlertClick(alerta) }
                )
            }
        }
    }
}

@Composable
private fun AlertCard(
    alerta: HomeAlert,
    onClick: () -> Unit
) {
    val colorAlerta = if (alerta.urgente) Color(0xFFFB7185) else when (alerta.tipo) {
        AlertType.STOCK_BAJO -> Color(0xFFFB7185)
        AlertType.PAGO_PENDIENTE -> Color(0xFFFBBF24)
        AlertType.DOCUMENTACION -> Color(0xFF60A5FA)
        AlertType.GENERAL -> Color(0xFFFBBF24)
    }

    val textoBoton = when (alerta.tipo) {
        AlertType.STOCK_BAJO -> "Pedir"
        AlertType.PAGO_PENDIENTE -> "Ver"
        AlertType.DOCUMENTACION -> "Revisar"
        AlertType.GENERAL -> "Ver"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colorAlerta.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "!",
                color = colorAlerta,
                fontWeight = FontWeight.Bold
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = alerta.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = alerta.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorAlerta
                )
            }

            TextButton(onClick = onClick) {
                Text(
                    text = textoBoton,
                    color = colorAlerta
                )
            }
        }
    }
}
