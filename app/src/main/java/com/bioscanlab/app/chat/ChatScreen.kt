package com.bioscanlab.app.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bioscanlab.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    // Desplaza automáticamente al final al recibir nuevos mensajes o al activarse el indicador de pensamiento
    LaunchedEffect(state.messages.size, state.isResponding) {
        val totalCount = state.messages.size + if (state.isResponding) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B1329),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color(0xFF2DD4BF),
                ),
                title = {
                    Column {
                        Text(
                            text = state.equipmentName.ifEmpty { "Asistente de Laboratorio" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            ),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "IA Especializada · Microbiología UTEQ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                },
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
                            contentDescription = "Volver al escáner",
                            tint = Color(0xFF2DD4BF),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = Color(0xFF090D16),
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Lista de Mensajes
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.messages) { message ->
                    MessageBubble(message)
                }

                // Indicador visual animado de que la IA está pensando / respondiendo
                if (state.isResponding) {
                    item {
                        ThinkingIndicatorBubble()
                    }
                }
            }

            // Barra inferior de entrada de texto
            ChatInputBar(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    viewModel.send(draft)
                    draft = ""
                },
                isResponding = state.isResponding,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        if (message.fromUser) {
            // Burbuja de usuario
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(Color(0xFF0F766E))
                    .border(1.dp, Color(0xFF14B8A6).copy(alpha = 0.5f), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        lineHeight = 20.sp,
                    ),
                )
            }
        } else {
            // Burbuja del Asistente IA con avatar y formato inteligente
            Row(
                modifier = Modifier.widthIn(max = 340.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Avatar del bot
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF2DD4BF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_smart_toy),
                        contentDescription = "Asistente IA",
                        tint = Color(0xFF2DD4BF),
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    // Texto formateado interpretando Markdown sin asteriscos crudos
                    MarkdownText(
                        text = message.text,
                        color = Color(0xFFF1F5F9),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        accentColor = Color(0xFF2DD4BF),
                    )

                    // Insignia de procedencia de la información
                    if (message.grounded) {
                        if (message.isOfficialGuide) {
                            // Información oficial de la guía: Insignia limpia y discreta (sin spamear archivos)
                            Row(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF064E3B).copy(alpha = 0.8f))
                                    .border(1.dp, Color(0xFF059669).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "✓ Información oficial del instructivo de laboratorio",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF6EE7B7),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                    ),
                                )
                            }
                        } else {
                            // Información técnica externa general: Aviso destacado de que NO está verificada en la guía
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF451A03).copy(alpha = 0.7f))
                                    .border(1.dp, Color(0xFFD97706).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "⚠️ Información técnica no verificada en el instructivo",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFFDE68A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    ),
                                )
                                Text(
                                    text = message.disclaimer ?: "Proviene de especificaciones técnicas generales de fabricante o literatura de laboratorio.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFFDE68A).copy(alpha = 0.85f),
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                    ),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Indicador visual animado que muestra cuando la IA está pensando / generando la respuesta.
 */
@Composable
private fun ThinkingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF2DD4BF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_smart_toy),
                contentDescription = null,
                tint = Color(0xFF2DD4BF),
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF2DD4BF).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MicroScan IA está analizando",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                ),
            )
            Spacer(Modifier.width(8.dp))

            // 3 Puntos animados pulsantes
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2DD4BF).copy(alpha = dot1Alpha)),
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2DD4BF).copy(alpha = dot2Alpha)),
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2DD4BF).copy(alpha = dot3Alpha)),
                )
            }
        }
    }
}

/**
 * Barra moderna de redacción con botón de envío circular y acentos neón.
 */
@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    isResponding: Boolean,
) {
    val canSend = draft.isNotBlank() && !isResponding

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1329))
            .border(width = 1.dp, color = Color(0xFF1E293B), shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = "Pregunta sobre uso, precauciones, piezas...",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                )
            },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF131D33),
                focusedBorderColor = Color(0xFF2DD4BF),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
        )

        Spacer(Modifier.width(8.dp))

        // Botón circular de envío
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (canSend) Color(0xFF2DD4BF) else Color(0xFF1E293B))
                .border(
                    width = 1.dp,
                    color = if (canSend) Color(0xFF5EEAD4) else Color(0xFF334155),
                    shape = CircleShape,
                )
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_send),
                contentDescription = "Enviar pregunta",
                tint = if (canSend) Color(0xFF0F172A) else Color(0xFF64748B),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
