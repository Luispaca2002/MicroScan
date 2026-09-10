# Modelo de deteccion

| Archivo | Que es |
|---|---|
| `best.pt` | Pesos originales de PyTorch, mejor epoca. Entregable "modelo original" |
| `last.pt` | Ultima epoca. Sirve para reanudar el entrenamiento |
| `best_int8.tflite` | Export a LiteRT que consume la app (copia de `app/src/main/assets/`) |

## Trazabilidad

Los tres archivos son de la misma corrida, verificado inspeccionando los
metadatos de cada uno:

- Arquitectura: **YOLO11n**, partiendo de `yolo11n.pt`
- Ultralytics **8.4.138** en los dos formatos
- Las **14 clases** presentes y en el mismo orden en `best.pt` y en el
  `metadata.json` embebido del `.tflite`
- Dataset de entrenamiento: `/content/microbiologia-1/data.yaml` (Roboflow)
- Fecha de export: 2026-09-04T03:22 UTC

## Reentrenar sin perder los pesos

El entrenamiento original escribio en `/content`, que Colab borra al
desconectarse. Para que no vuelva a pasar, `project` debe apuntar a Drive: los
pesos se guardan ahi epoca a epoca.

```python
from ultralytics import YOLO
YOLO('yolo11n.pt').train(
    data='/content/drive/MyDrive/microbiologia_proyecto/dataset_corregido/data.yaml',
    epochs=100, imgsz=640,
    project='/content/drive/MyDrive/microbiologia_proyecto/runs',
    name='v2',
)
```

## Evaluar sobre el split de test

La rubrica pide evaluar con imagenes nunca vistas en el entrenamiento. El
mAP50 de 0.967 es de validacion; este es el numero del split de test:

```python
YOLO('best.pt').val(data='data.yaml', split='test')
```

## Evaluacion sobre el split de test

150 imagenes que el modelo nunca vio durante el entrenamiento.

| Metrica | Validacion | **Test** |
|---|---|---|
| mAP50 | 0.967 | **0.971** |
| mAP50-95 | 0.765 | **0.690** |
| Precision | - | 0.970 |
| Recall | - | 0.979 |

Por clase, ordenado por mAP50-95 (peor localizacion arriba):

| Clase | mAP50 | mAP50-95 |
|---|---|---|
| incubadora memmert in110 | 0.995 | 0.433 |
| Estereo Microscopio Binocular Modelo BS-80 | 0.986 | 0.530 |
| Estereo Microscopio Thomas Scientific | 0.995 | 0.544 |
| Microscopio Olympus CX22 LED | 0.995 | 0.621 |
| Espectofotometro UV-5100B | 0.958 | 0.633 |
| Estufa de secado memmert ULE 600 | 0.925 | 0.634 |
| Incubadora de laboratorio SMI6 | 0.995 | 0.637 |
| Microscopio Motic RED 220 | 0.995 | 0.670 |
| Agitador Orbital JOANLAB OS-20 | 0.995 | 0.695 |
| Centrifugadora Sigma 201 | 0.995 | 0.742 |
| Balanza analitica PR Series Analytical | 0.941 | 0.805 |
| Autoclave ALL AMERICAN 25X-1 | 0.835 | 0.835 |
| Cabina de flujo laminar horizontal -PIVAS- | 0.995 | 0.904 |
| Bano maria Memmert WNB-14 | 0.995 | 0.971 |

### Lectura

**El modelo encuentra los equipos; lo que le cuesta es ajustar la caja.** El
mAP50 se sostiene en 0.97 sobre imagenes nuevas, pero el mAP50-95 cae de 0.765
a 0.690. Esa diferencia es solamente precision del contorno: a umbrales de IoU
exigentes la caja no calza tan fina. Para esta app no es grave, porque la caja
solo tiene que ser tocable y contener al equipo.

**El conjunto de test tiene 151 instancias en 150 imagenes**: practicamente un
equipo por foto. El modelo nunca se entreno ni se evaluo con varios equipos en
el mismo encuadre, asi que la deteccion simultanea que implementa la app no
esta respaldada por el dataset. Ver la nota en el README principal.

**El autoclave es la clase mas floja en deteccion** (mAP50 0.835, recall 0.833):
sobre 6 imagenes de test, falla una. Es tambien de las que menos fotos tiene.
