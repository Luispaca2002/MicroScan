# Conjunto de datos

Anotaciones en formato YOLO de los 14 equipos del Laboratorio de Microbiologia
de la UTEQ. Fuente: Roboflow, workspace `pagamos-claude`, proyecto
`microbiologia`, version 1, licencia CC BY 4.0.

## Que hay aca y que no

Estan las **anotaciones** (`*/labels/*.txt`, 3597 archivos) y la definicion del
dataset. **Las imagenes no**: son 267 MB y no corresponden en un repositorio de
codigo. Viven en Drive, en `microbiologia_proyecto/dataset_microbiologia.zip`.

Cada `.txt` sigue el formato YOLO: `<clase> <cx> <cy> <w> <h>`, normalizado a
[0,1]. El indice de clase se resuelve contra la lista de `data.yaml`.

## Reparto

| Split | Fotos originales | Tras aumentacion | % de originales |
|---|---|---|---|
| train | 1049 | 3147 (x3) | 70.0% |
| valid | 300 | 300 | 20.0% |
| test | 150 | 150 | 10.0% |
| **total** | **1499** | **3597** | 100% |

Verificado: no hay ninguna imagen sin su archivo de etiquetas en ningun split.

**La aumentacion se aplica solo a train**, que es lo correcto: aumentar
validacion o test falsearia las metricas al evaluar sobre variantes sinteticas
de fotos que ya se contaron.

Por eso el reparto de archivos (87.5 / 8.3 / 4.2) no es el reparto real del
conjunto. Lo que importa es el de fotos originales: **70 / 20 / 10**.

## Desvio respecto de la rubrica

La rubrica pide 70 / 15 / 15. El conjunto esta en 70 / 20 / 10: la particion de
entrenamiento coincide exactamente, pero validacion tiene 5 puntos de mas y
test 5 de menos.

Se opto por **no rehacer la particion**. Mover 75 imagenes de validacion a test
alcanzaria el 70/15/15 sin reentrenar, porque train no se toca, pero esas 75
imagenes participaron en la seleccion de la mejor epoca durante el
entrenamiento. Pasarlas a test contaminaria el conjunto de evaluacion con datos
que ya influyeron en la eleccion del modelo.

Las 150 imagenes de test actuales no se usaron para nada: ni para entrenar ni
para seleccionar. Un conjunto de prueba mas chico pero completamente limpio es
preferible a uno mas grande con fuga.

## Instancias por clase

Ver `ESTADISTICAS.md`. Resumen: entre 213 y 258 instancias de entrenamiento por
clase, salvo `Microscopio Motic RED 220` con 150 (un 35% por debajo de la
media). Aun asi esa clase obtuvo mAP50 de 0.995 en test, o sea que la menor
cantidad de fotos no fue el factor limitante.

## Escenas con un solo equipo

Test tiene **151 instancias en 150 imagenes**: practicamente un equipo por foto.
El modelo no se entreno con escenas de varios equipos en el mismo encuadre,
aunque la app si soporta detectarlos simultaneamente. Ver la nota del README
principal.
