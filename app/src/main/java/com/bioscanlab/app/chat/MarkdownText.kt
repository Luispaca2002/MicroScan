package com.bioscanlab.app.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renderizador de texto enriquecido que interpreta sintaxis Markdown común (.md):
 * - Convierte **negrita** en estilo en negrita sin mostrar asteriscos.
 * - Convierte *cursiva* en estilo cursiva sin mostrar asteriscos.
 * - Convierte listas con asteriscos o guiones (* o -) en viñetas limpias (•).
 * - Convierte encabezados (### o ##) en títulos destacados.
 * - Convierte código en línea (`valor`) en texto con estilo técnico.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val annotated = remember(text, accentColor) {
        parseMarkdownToAnnotatedString(text, accentColor)
    }

    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        style = style,
    )
}

internal fun parseMarkdownToAnnotatedString(
    markdown: String,
    accentColor: Color = Color(0xFF2DD4BF),
): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.lines()

        for (i in lines.indices) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            when {
                // Encabezados nivel 1, 2 o 3
                trimmed.startsWith("###") || trimmed.startsWith("##") || trimmed.startsWith("#") -> {
                    val headerText = trimmed.dropWhile { it == '#' || it == ' ' }.trim()
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontSize = 15.sp,
                        ),
                    ) {
                        append(headerText)
                    }
                }

                // Viñetas no ordenadas (- o *)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    val bulletContent = trimmed.substring(2).trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                        append("  •  ")
                    }
                    appendInlineStyledText(bulletContent, accentColor)
                }

                // Viñetas numeradas (1. 2. etc.)
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val dotIndex = trimmed.indexOf('.')
                    val numberPrefix = trimmed.substring(0, dotIndex + 2)
                    val rest = trimmed.substring(dotIndex + 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                        append("  $numberPrefix")
                    }
                    appendInlineStyledText(rest, accentColor)
                }

                // Línea común
                else -> {
                    appendInlineStyledText(rawLine, accentColor)
                }
            }

            if (i < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

/**
 * Procesa estilos dentro de una línea: **negrita**, *cursiva*, `código`.
 * Elimina los caracteres de marcado crudos (**, *, `).
 */
private fun AnnotatedString.Builder.appendInlineStyledText(
    line: String,
    accentColor: Color,
) {
    // Regex que busca tokens de negrita (**...**), cursiva (*...*), o código (`...`)
    val tokenRegex = Regex("""(\*\*.*?\*\*|\*[^*]+?\*|`[^`]+?`)""")
    var lastIndex = 0

    val matches = tokenRegex.findAll(line)
    for (match in matches) {
        val range = match.range
        if (range.first > lastIndex) {
            append(cleanRemainingSymbols(line.substring(lastIndex, range.first)))
        }

        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                val boldContent = token.substring(2, token.length - 2)
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldContent)
                }
            }
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                val codeContent = token.substring(1, token.length - 1)
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    append(codeContent)
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                val italicContent = token.substring(1, token.length - 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicContent)
                }
            }
            else -> {
                append(cleanRemainingSymbols(token))
            }
        }
        lastIndex = range.last + 1
    }

    if (lastIndex < line.length) {
        append(cleanRemainingSymbols(line.substring(lastIndex)))
    }
}

private fun cleanRemainingSymbols(text: String): String {
    return text.replace("**", "").replace("__", "")
}
