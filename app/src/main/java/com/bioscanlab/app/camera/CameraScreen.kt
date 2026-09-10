package com.bioscanlab.app.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioscanlab.app.detection.Detection
import com.bioscanlab.app.detection.FrameAnalyzer
import com.bioscanlab.app.detection.LabEquipment
import com.bioscanlab.app.rag.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Pantalla principal del escáner óptico:
 * - Visor de cámara en vivo con marco reticular de alta precisión.
 * - Barra superior HUD con información de aceleración, latencia y servidor.
 * - Panel inferior con información del equipo seleccionado y doble acción directa:
 *   [Ver Ficha Técnica] o [Preguntar a la IA].
 */
@Composable
fun CameraScreen(
    onViewSheet: (String) -> Unit,
    onAskAi: (String) -> Unit,
    onEquipmentSelected: (String) -> Unit = onViewSheet,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        PermissionRationale(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    val detectorState by viewModel.detectorState.collectAsStateWithLifecycle()
    val detections by viewModel.detections.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<Detection?>(null) }
    var showDemoPicker by remember { mutableStateOf(false) }
    var showServerConfigDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)),
    ) {
        when (val state = detectorState) {
            is DetectorState.Loading -> CenteredMessage("Iniciando escáner de laboratorio...", showSpinner = true)

            is DetectorState.Failed -> CenteredMessage("Error al inicializar el modelo:\n${state.message}")

            is DetectorState.Ready -> {
                // 1. Vista previa de la cámara
                CameraPreview(viewModel = viewModel)

                // 2. Marco reticular de visor científico
                ViewfinderOverlay(
                    hasDetections = detections.detections.isNotEmpty() || selected != null,
                )

                // 3. Cajas delimitadoras interactivas
                DetectionOverlay(
                    result = detections,
                    onDetectionSelected = { selected = it },
                    modifier = Modifier.fillMaxSize(),
                )

                // 4. Barra superior HUD (Marca, Acelerador, Latencia y Servidor)
                TopHudBar(
                    acceleratorName = state.accelerator.name,
                    inferenceMs = detections.inferenceMs,
                    detectionCount = detections.detections.size,
                    onOpenServerConfig = { showServerConfigDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding(),
                )

                // 5. Marco inferior de control y acciones
                BottomDashboard(
                    selected = selected,
                    hasDetectionsInView = detections.detections.isNotEmpty(),
                    onViewSheet = { className -> onViewSheet(className) },
                    onAskAi = { className -> onAskAi(className) },
                    onDismissSelection = { selected = null },
                    onOpenDemoPicker = { showDemoPicker = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding(),
                )

                // Selector de catálogo demo
                if (showDemoPicker) {
                    DemoPickerDialog(
                        onDismiss = { showDemoPicker = false },
                        onSelectEquipment = { index, name ->
                            showDemoPicker = false
                            selected = Detection(
                                classId = index,
                                score = 0.96f,
                                left = 0.15f,
                                top = 0.15f,
                                right = 0.85f,
                                bottom = 0.85f,
                            )
                        },
                    )
                }

                // Diálogo de configuración del servidor backend
                if (showServerConfigDialog) {
                    ServerConfigDialog(
                        onDismiss = { showServerConfigDialog = false },
                    )
                }
            }
        }
    }
}

/**
 * Visor reticular con esquinas de alta tecnología que enmarcan el área de escaneo.
 */
@Composable
private fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    hasDetections: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val reticleWidth = size.width * 0.78f
        val reticleHeight = size.height * 0.44f
        val left = (size.width - reticleWidth) / 2f
        val top = (size.height - reticleHeight) / 2.3f
        val right = left + reticleWidth
        val bottom = top + reticleHeight

        val cornerLen = 28.dp.toPx()
        val cornerStroke = 3.dp.toPx()
        val cornerColor = if (hasDetections) Color(0xFF2DD4BF) else Color(0x772DD4BF)

        // Superior Izquierda
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), cornerStroke)

        // Superior Derecha
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerLen, top), cornerStroke)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), cornerStroke)

        // Inferior Izquierda
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), cornerStroke)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLen), cornerStroke)

        // Inferior Derecha
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLen, bottom), cornerStroke)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), cornerStroke)

        // Cruz sutil de centrado óptico
        val centerX = size.width / 2f
        val centerY = top + reticleHeight / 2f
        val crosshair = 8.dp.toPx()
        val crossColor = Color(0x332DD4BF)
        drawLine(crossColor, Offset(centerX - crosshair, centerY), Offset(centerX + crosshair, centerY), 1.5.dp.toPx())
        drawLine(crossColor, Offset(centerX, centerY - crosshair), Offset(centerX, centerY + crosshair), 1.5.dp.toPx())
    }
}

/**
 * Barra superior HUD con información del sistema en tiempo real.
 */
@Composable
private fun TopHudBar(
    acceleratorName: String,
    inferenceMs: Long,
    detectionCount: Int,
    onOpenServerConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF20B132B),
        border = BorderStroke(1.dp, Color(0x332DD4BF)),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Fila 1: Logo + Nombre + Botón Servidor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF0D9488), Color(0xFF0284C7)),
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🔬", fontSize = 14.sp)
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "MicroScan",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0F766E),
                            ) {
                                Text(
                                    text = "AI SCANNER",
                                    color = Color(0xFF99F6E4),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "Microbiología · UTEQ",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                        )
                    }
                }

                // Botón Servidor
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.clickable { onOpenServerConfig() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("⚙️", fontSize = 12.sp)
                        Text(
                            text = "Servidor",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Fila 2: Chips de aceleración y estado de escaneo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34D399)),
                    )
                    Text(
                        text = "⚡ $acceleratorName · ${inferenceMs}ms",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (detectionCount > 0) Color(0xFF064E3B) else Color(0xFF1E293B),
                ) {
                    Text(
                        text = if (detectionCount > 0) "🎯 $detectionCount en cuadro" else "📡 Escaneando...",
                        color = if (detectionCount > 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Marco inferior de información y acciones (Bottom Control Deck):
 * Si hay un equipo seleccionado, muestra su nombre, confianza y dos botones destacados:
 * 📄 "Ver Ficha Técnica" o 💬 "Preguntar a la IA".
 */
@Composable
private fun BottomDashboard(
    selected: Detection?,
    hasDetectionsInView: Boolean,
    onViewSheet: (String) -> Unit,
    onAskAi: (String) -> Unit,
    onDismissSelection: () -> Unit,
    onOpenDemoPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xF40B132B),
        border = BorderStroke(1.dp, Color(0x442DD4BF)),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected != null) {
                // Caso 1: Equipo seleccionado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔬 EQUIPO SELECCIONADO",
                            color = Color(0xFF2DD4BF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = selected.label,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF065F46),
                            ) {
                                Text(
                                    text = "Precisión ${(selected.score * 100).roundToInt()}%",
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = "· Selecciona qué deseas hacer:",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    // Botón para cerrar selección y volver a escanear libremente
                    IconButton(
                        onClick = onDismissSelection,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text("✕", color = Color(0xFF94A3B8), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Botones de acción dual requeridos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Botón 1: Ver Ficha Técnica
                    OutlinedButton(
                        onClick = { onViewSheet(selected.label) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF0D9488)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2DD4BF),
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("📄", fontSize = 15.sp)
                            Text(
                                text = "Ficha Técnica",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // Botón 2: Preguntar a la IA directamente
                    Button(
                        onClick = { onAskAi(selected.label) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0D9488),
                            contentColor = Color.White,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("💬", fontSize = 15.sp)
                            Text(
                                text = "Preguntar a IA",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Enlace para cambiar equipo de demo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = onOpenDemoPicker,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "🧪 Cambiar equipo de prueba (Demo)",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                // Caso 2: Sin equipo seleccionado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (hasDetectionsInView) "👆" else "📷",
                            fontSize = 20.sp,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasDetectionsInView) "¡Equipo detectado en cuadro!" else "Buscando equipos de laboratorio...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (hasDetectionsInView) "Toca el recuadro del equipo para seleccionarlo." else "Apunta al equipo o selecciona uno del catálogo demo.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    }
                }

                Button(
                    onClick = onOpenDemoPicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF2DD4BF),
                    ),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🧪", fontSize = 16.sp)
                        Text(
                            text = "Seleccionar equipo del catálogo (Demo)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diálogo interactivo para seleccionar cualquier equipo del catálogo demo.
 */
@Composable
private fun DemoPickerDialog(
    onDismiss: () -> Unit,
    onSelectEquipment: (Int, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🧪", fontSize = 20.sp)
                Text("Catálogo de Equipos", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(LabEquipment.CLASS_NAMES) { index, name ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEquipment(index, name) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            Text(
                                text = name,
                                textAlign = TextAlign.Start,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

/**
 * Diálogo de configuración del backend.
 */
@Composable
private fun ServerConfigDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var inputUrl by remember { mutableStateOf(ServerConfig.getUrl(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Servidor Backend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ingresa la URL del servidor (ej. túnel Cloudflare o IP local) para conectar desde cualquier red:",
                    style = MaterialTheme.typography.bodySmall,
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
                    onDismiss()
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun CameraPreview(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(Unit) {
        val detector = viewModel.detector ?: return@LaunchedEffect
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(
                    analysisExecutor,
                    FrameAnalyzer(detector) { result -> viewModel.publish(result) },
                )
            }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
        )
    }
}

@Composable
private fun CenteredMessage(text: String, showSpinner: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = Color(0xFF2DD4BF))
        }
        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🔬",
            fontSize = 48.sp,
        )
        Text(
            text = "MicroScan necesita acceso a la cámara para reconocer los equipos del laboratorio de microbiología.",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = onRequest,
            modifier = Modifier.padding(top = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
        ) {
            Text("Permitir acceso a cámara")
        }
    }
}
