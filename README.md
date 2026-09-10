# BioScanLab

App Android de reconocimiento en tiempo real de equipos de laboratorio de
microbiología, con YOLO11n exportado a LiteRT.

## Cómo abrirlo

1. Android Studio → **Open** → `C:\BioScanLab`
2. Esperá el Gradle sync. El SDK ya está apuntado en `local.properties` (`C:\Android`).
3. Run sobre un dispositivo físico. **En emulador no sirve**: necesita cámara real.

## El modelo

`app/src/main/assets/best_int8.tflite` — YOLO11n, Ultralytics 8.4.138, 14 clases.

Datos verificados inspeccionando el propio `.tflite` (no asumidos):

| | |
|---|---|
| Input | `[1, 3, 640, 640]` float32 **NCHW** (channel-first) |
| Output | `[1, 18, 8400]` float32 channel-first — 4 box + 14 clases |
| Cajas | `cx, cy, w, h` ya normalizadas a `[0..1]` |
| NMS | **no incluido** (`end2end: false`) — se aplica en Kotlin |
| Cuantización | int8 interna, pero la **interfaz es float32** |

El layout NCHW es la trampa principal: casi todo el código de ejemplo de TFLite
asume NHWC `[1,640,640,3]`. Escribir el tensor intercalado no produce ningún
error — el modelo simplemente detecta basura. Ver `EquipmentDetector.writeNchwInput()`.

## Arquitectura

```
detection/
  LabEquipment.kt       Las 14 clases en el orden exacto del data.yaml
  Detection.kt          Modelos de datos (coords normalizadas)
  EquipmentDetector.kt  Carga, letterbox, inferencia, decode, NMS
  FrameAnalyzer.kt      Analyzer de CameraX, fuera del UI thread
camera/
  CameraViewModel.kt    Dueño del ciclo de vida del detector
  CameraScreen.kt       PreviewView + permisos + binding de CameraX
  DetectionOverlay.kt   Dibuja todas las cajas y resuelve el tap
  BoxMapper.kt          Normalizado <-> píxeles, y heurística de selección
chat/
  ChatScreen.kt         UI de chat
  ChatViewModel.kt      Recibe el nombre de clase, habla con el backend
rag/                  Cliente del backend (modelos + HTTP)
sheet/
  EquipmentSheetScreen  Ficha tecnica del equipo
nav/
  AppNavigation.kt      NavHost: camera -> sheet -> chat
```

## Backend RAG (modulo `:server`, Kotlin + Ktor)

```bash
./gradlew :server:run
```

Imprime al arrancar la IP de tu maquina en la red local. Esa es la que va en
`HttpRagClient.DEFAULT_BASE_URL` y en `network_security_config.xml`.

Variables de entorno:

| Variable | Default | Para que |
|---|---|---|
| `LLM_PROVIDER` | `ollama` | `ollama`, `openai` o `claude` |
| `OLLAMA_MODEL` | `qwen2.5:3b` | modelo local |
| `OPENAI_BASE_URL` | OpenRouter | cualquier API compatible con OpenAI |
| `OPENAI_API_KEY` | - | si `LLM_PROVIDER=openai` |
| `OPENAI_MODEL` | `deepseek/deepseek-chat-v3-0324:free` | modelo remoto |
| `ANTHROPIC_API_KEY` | - | si `LLM_PROVIDER=claude` |
| `PORT` | `8080` | puerto |

### Modelos gratuitos

**Local (sin key, sin internet).** Es el modo por defecto:

```bash
ollama pull qwen2.5:3b
./gradlew :server:run
```

En una GTX 1650 (4 GB) un modelo de 3B entra completo en VRAM. Uno de 14B no:
corre parcialmente en CPU y tarda entre 15 y 25 segundos por respuesta, lo que
sirve para probar pero se hace largo en una demo en vivo.

**Remoto gratuito.** El adaptador `OpenAiCompatibleLlm` habla con cualquier
proveedor de API compatible con OpenAI, asi que las variantes gratuitas de
DeepSeek y Qwen en OpenRouter o Groq funcionan cambiando solo dos variables:

```bash
LLM_PROVIDER=openai OPENAI_API_KEY=... OPENAI_MODEL=qwen/qwen-2.5-72b-instruct:free ./gradlew :server:run
```

Endpoints: `GET /health`, `GET /sheet?equipment=...`, `POST /ask`.

**Como esta fundamentado (grounding).** La recuperacion es BM25 sobre
`server/corpus/*.md`, troceado por equipo y apartado. Tres decisiones sostienen
que el asistente no invente:

1. La ficha tecnica se arma **sin pasar por el LLM**: es una transcripcion
   estructurada de los fragmentos. No hay forma de que aparezca un dato que no
   este en los documentos.
2. Si la recuperacion no devuelve nada, **no se llama al LLM**. Sin contexto no
   hay respuesta fundamentada posible, y llamarlo igual seria invitarlo a
   inventar.
3. La busqueda exige que el **titulo** del corpus se corresponda con la clase
   detectada (60% de cobertura de terminos), no solo que coincidan palabras del
   cuerpo. Sin esto, "Balanza analitica PR Series" recuperaba la seccion de la
   INCUBADORA porque su texto menciona "investigacion clinica y analitica".

Verificado end-to-end con `qwen2.5:3b` local, 9 de 9 casos correctos:

| Caso | Resultado | Latencia |
|---|---|---|
| Nivel minimo de agua del bano maria | "97 mm... puede fundir el sistema de calefaccion" | 3.6 s |
| Limite maximo de masa de la balanza | "220 g" | 3.8 s |
| Distancia interpupilar del microscopio | responde con el procedimiento | 4.4 s |
| Colocacion de placas de Petri | "posicion invertida, maximo 20 kg por bandeja" | 4.9 s |
| Precauciones del espectrofotometro | responde con las 3 del manual | 5.9 s |
| Precio y distribuidor del microscopio | rechaza | 4.2 s |
| Ano de invencion de la balanza | rechaza | 4.0 s |
| Cualquier pregunta sobre los 6 equipos sin docs | rechaza sin llamar al LLM | **0.0 s** |

Las respuestas fundamentadas citan 4 secciones de fuente cada una. `GET /sheet`
no pasa por el LLM y responde en menos de 60 ms.

**Cobertura actual del corpus: 8 de 14 clases**, todas mapeadas al equipo
correcto y sin contaminacion cruzada. Las otras 6 (agitador orbital, autoclave,
cabina de flujo laminar, centrifugadora, estufa de secado e incubadora SMI6)
responden correctamente "no dispongo de informacion suficiente".

Por tema, sobre esas 8 clases:

| Tema exigido por la rubrica | Estado |
|---|---|
| Nombre y funcion | cubierto |
| Procedimiento de uso | cubierto |
| Riesgos / precauciones | cubierto |
| Componentes principales | **vacio** |
| Elementos de proteccion personal | **vacio** |
| Guias de practicas de la UTEQ | **vacio** |

## Los TODO que quedan para vos

1. **`gradle.properties` -> `bioscanlab.backendUrl`** — unica linea a cambiar
   al moverse de red. El servidor imprime su IP al arrancar. No hace falta
   tocar codigo fuente ni el network security config.
2. **`server/corpus/`** — agregar documentos. Basta con dejarlos como `.md`
   con `## EQUIPO` y `### APARTADO`; el servidor los indexa al arrancar.

## Ajustes de tuning

En `EquipmentDetector`: `DEFAULT_CONFIDENCE` (0.40) y `DEFAULT_IOU` (0.45).
En `FrameAnalyzer`: `minIntervalMs` (100 ms = tope de 10 inferencias/s).

La barra superior del preview muestra en vivo el acelerador elegido
(GPU/NNAPI/CPU), los ms por inferencia y cuántos equipos se están viendo.
