package com.example.gestorempresa.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.gestorempresa.ui.home.CatalogSelection

@Composable
fun DetailScreen(content: DetailContent, onBack: () -> Unit, modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {}, onOpenRelated: (CatalogSelection) -> Unit = {}) {
    LazyColumn(modifier = modifier.fillMaxSize().testTag("detail-screen"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Volver al listado") } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(content.title, style = MaterialTheme.typography.headlineMedium)
                Text(content.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content.sections.forEach { section ->
            item(key = "section-${section.title}") {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                        section.fields.forEach { field ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(field.value, style = MaterialTheme.typography.bodyLarge)
                                field.related?.let { related -> TextButton(onClick = { onOpenRelated(related) }) { Text("Abrir material") } }
                            }
                        }
                    }
                }
            }
        }
        item { actions() }
    }
}
