# Guía de Uso Oficial de MicroScan 🔬

**MicroScan** es una aplicación móvil para Android diseñada para el reconocimiento en tiempo real de equipos en laboratorios de microbiología, la consulta inmediata de fichas técnicas y la asistencia interactiva mediante inteligencia artificial fundamentada (RAG).

---

## 📋 Tabla de Contenidos
1. [Introducción y Objetivos](#1-introducción-y-objetivos)
2. [Requisitos del Sistema e Instalación](#2-requisitos-del-sistema-e-instalación)
3. [Modos de Conectividad y Funcionamiento](#3-modos-de-conectividad-y-funcionamiento)
4. [Guía de la Interfaz y Pantallas](#4-guía-de-la-interfaz-y-pantallas)
   - [4.1. Escáner Óptico en Vivo (Pantalla Principal)](#41-escáner-óptico-en-vivo-pantalla-principal)
   - [4.2. Ficha Técnica del Equipo](#42-ficha-técnica-del-equipo)
   - [4.3. Asistente Inteligente con IA (Chat RAG)](#43-asistente-inteligente-con-ia-chat-rag)
5. [Catálogo de los 14 Equipos Soportados](#5-catálogo-de-los-14-equipos-soportados)
6. [Buenas Prácticas para el Escaneo en Laboratorio](#6-buenas-prácticas-para-el-escaneo-en-laboratorio)
7. [Preguntas Frecuentes y Solución de Problemas (FAQ)](#7-preguntas-frecuentes-y-solución-de-problemas-faq)

---

## 1. Introducción y Objetivos

En un laboratorio de microbiología, la correcta manipulación de instrumentos y equipos es esencial para garantizar la seguridad del personal, evitar la contaminación de cultivos y prevenir averías costosas. 

**MicroScan** resuelve tres necesidades fundamentales:
1. **Identificación Inmediata**: Detecta y clasifica equipos ópticamente a través de la cámara del smartphone mediante una red neuronal profunda **YOLO11n** optimizada en formato **LiteRT (INT8)**.
2. **Acceso Rápido a Información Crítica**: Despliega fichas técnicas estandarizadas (funciones, componentes, procedimiento de encendido/apagado, EPP y riesgos) sin necesidad de hojear manuales físicos.
3. **Asesoría Especializada en Tiempo Real**: Incorpora un asistente conversacional potenciado por el modelo **Google Gemini** con arquitectura RAG (Retrieval-Augmented Generation), capaz de resolver dudas operativas y de bioseguridad citando fuentes verificadas.

---

## 2. Requisitos del Sistema e Instalación

### Requisitos Mínimos del Dispositivo
- **Sistema Operativo**: Android 8.0 (Oreo / API 26) o superior.
- **Cámara**: Sensor de cámara trasera de al menos 8 MP con soporte para CameraX.
- **Memoria RAM**: 3 GB o superior.
- **Conectividad**:
  - Para detección de equipos y fichas técnicas: **100% Offline** (no requiere internet).
  - Para consultas en lenguaje natural con la IA: **Conexión a Internet** (WiFi o Datos Móviles 4G/5G).

### Pasos de Instalación del APK

1. **Obtener el instalador**:
   - Localiza el archivo `app-debug.apk` compilado en la carpeta del proyecto:
     `app/build/outputs/apk/debug/app-debug.apk`.
2. **Transferir al Teléfono**:
   - Puedes enviarlo mediante cable USB, WhatsApp, Telegram, Google Drive o correo electrónico.
3. **Habilitar Instalación de Apps Desconocidas**:
   - Al abrir el archivo `.apk`, Android solicitará autorización. Dirígete a *Ajustes* → *Seguridad y privacidad* → *Instalar aplicaciones desconocidas* y concede el permiso para tu navegador o gestor de archivos.
4. **Completar la Instalación**:
   - Presiona **Instalar** y espera a que concluya el proceso.
5. **Otorgar Permiso de Cámara**:
   - Al iniciar MicroScan por primera vez, se mostrará una solicitud para acceder a la cámara. Presiona **"Permitir mientras la app está en uso"**.

---

## 3. Modos de Conectividad y Funcionamiento

MicroScan fue desarrollada con una arquitectura autónoma y flexible:

| Característica | Modo Sin Conexión (Offline) | Modo con Internet (Cloud Gemini) |
|---|---|---|
| **Escaneo y Detección de Equipos** | ✅ Operativo (Inferencia en chip local) | ✅ Operativo |
| **Consulta de Fichas Técnicas** | ✅ Operativo (Corpus local en Assets) | ✅ Operativo |
| **Chat Asistente IA** | ⚠️ Deshabilitado (Requiere red) | ✅ Operativo (Consultas directas a Gemini) |
| **Dependencia de PC o Servidor** | ❌ Ninguna | ❌ Ninguna (No necesita PC encendida) |

> 💡 **Nota importante**: No necesitas estar conectado a la misma red WiFi de tu computadora ni tener una terminal abierta. El APK funciona de manera completamente independiente en cualquier lugar.

---

## 4. Guía de la Interfaz y Pantallas

### 4.1. Escáner Óptico en Vivo (Pantalla Principal)

Al abrir la aplicación, entrarás directamente a la pantalla de detección por cámara:

```
+-------------------------------------------------------+
|  🔬 MicroScan                [GPU]  58ms  |  1 Eq.    |  <- HUD Técnico
+-------------------------------------------------------+
|                                                       |
|                     [  +  ]                           |  <- Retícula de enfoque
|                                                       |
|            +-------------------------+                |
|            | Microscopio Olympus ... |                |  <- Bounding Box táctil
|            +-------------------------+                |
|                                                       |
|                                                       |
|  [ Probar equipo (Modo Demo)                      v ] |  <- Selector Demo
+-------------------------------------------------------+
|  EQUIPO IDENTIFICADO                                  |
|  Microscopio Olympus CX22 LED                         |
|  Confianza del modelo: 94%                            |
|                                                       |
|  [ 📄 Ficha Técnica ]        [ 💬 Preguntar a IA ]   |  <- Botones de acción
+-------------------------------------------------------+
```

#### Elementos Clave:
1. **Barra Superior (HUD Técnico)**:
   - **Acelerador de Hardware**: Muestra si la red neuronal corre sobre `[GPU]`, `[NNAPI]` o `[CPU]`.
   - **Latencia**: Tiempo exacto de procesamiento por cuadro (ej. `45 - 65 ms`, garantizando ~15-20 FPS fluidos).
   - **Contador**: Cantidad de equipos detectados en el campo visual en ese instante.
2. **Retícula y Bounding Boxes**:
   - Los equipos reconocidos se enmarcan en rectángulos de color turquesa con el nombre del modelo y el porcentaje de confianza.
   - **Selección Táctil**: Puedes tocar directamente cualquier caja en la pantalla para seleccionarla.
3. **Selector de Modo Demo**:
   - Permite desplegar una lista con los 14 equipos de laboratorio. Es ideal para realizar demostraciones o probar las funciones de la app sin estar físicamente en el laboratorio.
4. **Panel de Control Inferior**:
   - Presenta el equipo actualmente enfocado o seleccionado.
   - **Botón "Ficha Técnica"**: Abre la documentación técnica del equipo.
   - **Botón "Preguntar a IA"**: Inicia el chat con el asistente conversacional con dicho equipo ya contextualizado.

---

### 4.2. Ficha Técnica del Equipo

Esta pantalla presenta la información técnica estructurada, extraída directamente del instructivo oficial de microbiología:

- **Encabezado**: Nombre y marca del equipo, con botón de retroceso (`←`) en la esquina superior izquierda para regresar al escáner.
- **Secciones Disponibles**:
  1. **Función Principal**: Propósito científico y principio de operación del equipo.
  2. **Procedimiento de Uso**: Pasos cronológicos recomendados para el encendido, calibración, operación y apagado.
  3. **Componentes Principales**: Partes físicas, perillas de ajuste, sensores y paneles de control.
  4. **Elementos de Protección Personal (EPP)**: Indumentaria obligatoria (bata, gafas UV, guantes de nitrilo, etc.).
  5. **Riesgos y Precauciones de Seguridad**: Alertas sobre altas temperaturas, riesgo biológico, radiación UV o choque eléctrico.
  6. **Mantenimiento y Limpieza**: Soluciones desinfectantes aprobadas y cuidados preventivos.
- **Fuentes Citadas**: Cada ficha detalla el capítulo o sección del manual del cual se extrajo la información.
- **Acceso Directo al Chat**: En la parte inferior, un botón flotante permite pasar directamente a consultar a la IA sobre ese equipo.

---

### 4.3. Asistente Inteligente con IA (Chat RAG)

El asistente conversacional combina la potencia de los modelos generativos con los manuales técnicos del laboratorio:

#### Características del Chat:
1. **Indicador de Pensamiento Animado**:
   - Mientras la IA procesa la consulta, se muestra una burbuja interactiva: *"MicroScan IA está analizando..."* con animación pulsante.
2. **Formato Markdown Limpio**:
   - Las respuestas se formatean automáticamente con viñetas, títulos destacados y listas legibles, eliminando símbolos crudos (`*`, `#`).
3. **Insignias de Verificación de Información**:
   - 🟢 **Fuente Oficial Verificada**: Aparece cuando la respuesta proviene fielmente del manual oficial del laboratorio.
   - 🟡 **Información Complementaria (No Verificada en el Manual)**: Si el usuario pregunta algo que no está en el manual pero es de microbiología/laboratorio (por ejemplo, principios físicos avanzados o comparativas técnicas), la IA responde con rigor científico pero advierte de forma explícita que se trata de información complementaria.
4. **Filtro de Dominio Estricto (Guardrail)**:
   - Si se formula una pregunta ajena al laboratorio (recetas de cocina, deportes, películas, etc.), la IA responde:
     > *"No se encuentra información sobre este tema. Como asistente técnico del laboratorio de microbiología, únicamente puedo resolver dudas sobre los equipos del laboratorio, bioseguridad, EPP y procedimientos técnicos."*
5. **Navegación Intuitiva**:
   - Botón superior para **Volver al Escáner** en cualquier momento.
   - Botón para **Limpiar Historial** de chat.

---

## 5. Catálogo de los 14 Equipos Soportados

El modelo de visión por computadora de MicroScan está entrenado específicamente para reconocer los siguientes 14 equipos de microbiología:

| N° | Nombre de Clase en el Modelo | Categoría | Función Clave |
|:---:|---|---|---|
| **0** | `Agitador Orbital JOANLAB OS-20` | Mezclado | Agitación continua de cultivos líquidos |
| **1** | `Autoclave ALL AMERICAN 25X-1` | Esterilización | Esterilización por vapor a presión y alta temperatura |
| **2** | `Balanza analitica PR Series Analytical` | Metrología | Pesaje de alta precisión de reactivos y medios |
| **3** | `Bano maria  Memmert WNB-14` | Termorregulación | Incubación a temperatura constante por baño hídrico |
| **4** | `Cabina de flujo laminar horizontal -PIVAS- BBS-H1500B BBS-H1800B` | Bioseguridad | Protección del producto y flujo de aire estéril |
| **5** | `Centrifugadora Sigma 201` | Separación | Separación de fases celulares por fuerza centrífuga |
| **6** | `Espectofotometro UV-5100B` | Análisis Óptico | Medición de absorbancia y densidad óptica celular |
| **7** | `Estereo Microscopio Binocular Modelo BS-80` | Microscopía | Observación tridimensional de colonias a bajo aumento |
| **8** | `Estereo Microscopio Thomas Scientific` | Microscopía | Lupa estereoscópica para disección y macrocolonias |
| **9** | `Estufa de secado memmert ULE 600` | Secado / Calor | Desecación de muestras y esterilización por calor seco |
| **10** | `Incubadora de laboratorio SMI6` | Cultivo | Ambiente controlado de temperatura para microorganismos |
| **11** | `Microscopio Motic RED 220` | Microscopía Óptica | Observación óptica de frotis teñidos y bacterias |
| **12** | `Microscopio Olympus CX22 LED` | Microscopía Óptica | Microscopía de campo claro con iluminación LED |
| **13** | `incubadora memmert in110` | Cultivo | Incubación microbiológica de alta estabilidad térmica |

---

## 6. Buenas Prácticas para el Escaneo en Laboratorio

Para obtener una detección instantánea y una tasa de acierto óptima (mAP50 > 97%):

1. **Distancia Recomendada**:
   - Mantén el teléfono entre **0.8 y 1.5 metros** de distancia respecto al equipo.
2. **Ángulo y Encuadre**:
   - Procura que el frontal del equipo (panel de mandos, perillas o display) ocupe al menos el 40% del visor de la cámara.
3. **Iluminación**:
   - Evita contraluces fuertes directos provenientes de ventanas o lámparas UV encendidas.
4. **Estabilidad**:
   - Mantén el dispositivo firme por 1 o 2 segundos mientras el modelo procesa la inferencia en tiempo real.
5. **Seguridad Biológica**:
   - Siempre higieniza tu smartphone con toallitas con alcohol al 70% antes y después de ingresar al área limpia del laboratorio.

---

## 7. Preguntas Frecuentes y Solución de Problemas (FAQ)

### ¿La aplicación funciona sin conexión a Internet?
**Sí.** El reconocimiento visual de los 14 equipos mediante la cámara y la lectura completa de las fichas técnicas operan **100% de manera local y offline**. Únicamente se requiere conexión a internet (WiFi o datos móviles) cuando desees hacer preguntas abiertas en el chat con la IA.

### ¿Necesito tener mi computadora encendida para que la app funcione?
**No.** MicroScan se comunica directamente con la API de Google Gemini en la nube. Puedes compartir el archivo APK con cualquier persona y le funcionará de manera inmediata en su teléfono.

### ¿Qué hago si la pantalla de la cámara aparece en negro?
1. Ve a los *Ajustes de tu celular* → *Aplicaciones* → *MicroScan* → *Permisos*.
2. Asegúrate de que el permiso de **Cámara** esté configurado en *"Permitir"*.
3. Cierra la aplicación de la multitarea y vuelve a abrirla.

### ¿Por qué la IA responde que no encuentra información cuando pregunto algo no relacionado?
MicroScan cuenta con un sistema de bioseguridad y protección temática. Ha sido configurado específicamente como un asistente de laboratorio de microbiología. No responderá sobre temas ajenos para evitar distracciones o información no fidedigna durante las prácticas experimentales.

### ¿Cómo pruebo la aplicación si no estoy dentro del laboratorio?
En la pantalla de la cámara, utiliza el menú desplegable **"Probar equipo (Modo Demo)"**. Podrás elegir cualquiera de los 14 equipos de la lista para simular su detección y consultar sus fichas técnicas y chat de inmediato.

---
*Manual generado para el proyecto de investigación e instrumentación de microbiología - MicroScan.*
