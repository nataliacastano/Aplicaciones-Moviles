package com.example.gestorempresa.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BalanceCard(
    ingresos: Double,
    gastos: Double,
    modifier: Modifier = Modifier
) {
    val balance = ingresos - gastos
    val positivo = balance >= 0

    val colorBalance = if (positivo) {
        Color(0xFF34D399)
    } else {
        Color(0xFFFB7185)
    }

    val formatoEuro = NumberFormat.getCurrencyInstance(
        Locale.forLanguageTag("es-ES")
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BALANCE OPERATIVO MES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (positivo) "Positivo" else "Negativo",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorBalance
                )
            }

            Text(
                text = (if (balance > 0) "+" else "") +
                        formatoEuro.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorBalance
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "INGRESOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatoEuro.format(ingresos),
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column {
                    Text(
                        text = "GASTOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatoEuro.format(gastos),
                        color = Color(0xFFFB7185),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
