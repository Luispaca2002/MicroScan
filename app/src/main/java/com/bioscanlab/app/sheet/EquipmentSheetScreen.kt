package com.bioscanlab.app.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.bioscanlab.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioscanlab.app.rag.ServerConfig
import com.bioscanlab.app.rag.SourceRef

/**
 * Ficha tecnica del equipo detectado.
 *
 * Es el paso intermedio que pide la rubrica: caja detectada -> boton
 * "informacion tecnica" -> ESTA pantalla -> chat con el asistente.
 *
 * Todo el contenido viene del backend RAG, no de datos hardcodeados en la app:
 * de ese modo la ficha tambien queda respaldada por los documentos del
 * laboratorio y puede citar sus fuentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentSheetScreen(
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    viewModel: EquipmentSheetViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Copia local: 'state' es una propiedad delegada y Kotlin no puede
    // hacer smart cast sobre sus campos.
    val sheet = state.sheet
    val context = LocalContext.current
    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ficha técnica", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 4.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Volver",
                            tint = Color(0xFF2DD4BF),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showConfigDialog = true }) {
                        Text("Servidor")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                item {
                    Text(
                        text = state.className,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                if (state.isLoading) {
                    item {
                        CircularProgressIndicator(Modifier.padding(top = 32.dp))
                    }
                } else if (state.isConnectionError) {
                    item {
                        ConnectionErrorCard(
                            onRetry = { viewModel.loadSheet() },
                            onConfigure = { showConfigDialog = true },
                        )
                    }
                } else if (sheet == null || sheet.isEmpty) {
                    item { NoDataCard() }
                } else {
                    item { Section("Funcion", listOf(sheet.funcion)) }
                    item { Section("Componentes principales", sheet.componentes) }
                    item { Section("Procedimiento de uso", sheet.procedimiento) }
                    item { Section("Proteccion personal (EPP)", sheet.proteccionPersonal) }
                    item { Section("Riesgos asociados", sheet.riesgos) }
                    item { Section("Mantenimiento", sheet.mantenimiento) }
                    item { Sources(sheet.sources) }
                }
            }

            Button(
                onClick = { onOpenChat(state.className) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("Preguntar al asistente")
            }
        }
    }

    if (showConfigDialog) {
        var inputUrl by remember { mutableStateOf(ServerConfig.getUrl(context)) }

        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configuración del Servidor") },
            text = {
                Column {
                    Text(
                        text = "Ingresa la URL pública del backend (túnel Cloudflare o servidor en la nube):",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("URL del backend") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { inputUrl = ServerConfig.resetToDefault(context) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Restablecer por defecto")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputUrl.isNotBlank()) {
                            ServerConfig.setUrl(context, inputUrl)
                        }
                        showConfigDialog = false
                        viewModel.loadSheet()
                    },
                ) {
                    Text("Guardar y reintentar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, items: List<String>) {
    val visible = items.filter { it.isNotBlank() }
    if (visible.isEmpty()) return

    Column(Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        visible.forEach { line ->
            Text(
                text = if (visible.size > 1) "•  $line" else line,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Las fuentes se muestran siempre que haya contenido: es un requisito de la
 * rubrica, no un detalle de presentacion.
 */
@Composable
private fun Sources(sources: List<SourceRef>) {
    if (sources.isEmpty()) return

    Column(Modifier.padding(bottom = 24.dp)) {
        Text("Fuentes consultadas", style = MaterialTheme.typography.titleSmall)
        sources.forEach { source ->
            Text(
                text = "•  ${source.document} - ${source.section}",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ConnectionErrorCard(
    onRetry: () -> Unit,
    onConfigure: () -> Unit,
) {
    val currentUrl = ServerConfig.getUrl()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "No se pudo conectar con el servidor",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "No hay conexión con el backend en:\n$currentUrl\n\n" +
                    "Asegúrate de que el servidor esté activo o pulsa 'Cambiar URL' para usar el túnel de internet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onConfigure,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text("Cambiar URL")
                }
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
private fun NoDataCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "No dispongo de informacion suficiente",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Los documentos del laboratorio no contienen datos para este " +
                    "equipo. Consulta al docente o al responsable del laboratorio.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
