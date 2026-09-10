# Entrenamiento

| Archivo | Que es |
|---|---|
| `entrenamiento_microbiologia.ipynb` | Notebook de Colab: descarga del dataset, entrenamiento, export a LiteRT y validacion del modelo cuantizado |
| `data.yaml` | Definicion del dataset: 14 clases y rutas de los splits |

## Dataset

Roboflow, workspace `pagamos-claude`, proyecto `microbiologia`, version 1,
licencia CC BY 4.0. El notebook lo descarga en formato `yolov11`.

Los 14 nombres de clase de `data.yaml` estan transcritos literalmente en
`LabEquipment.CLASS_NAMES` de la app, verificado caracter por caracter. Ese
orden es el contrato con el modelo: el indice que devuelve la inferencia se
resuelve contra esa lista.

## Credenciales

La API key de Roboflow estaba escrita en el notebook. Se reemplazo por
`getpass()`, que la pide al ejecutar. **La key original quedo expuesta y debe
regenerarse en Roboflow.**

## Donde quedaron los pesos

El notebook entrena con `project` apuntando a Drive:

```
/content/drive/MyDrive/microbiologia_proyecto/microbiologia_v1/weights/
```

Por eso `best.pt` sobrevivio a la desconexion de Colab: lo que se pierde al
desconectarse es `/content`, no Drive.

## Export

```python
model.export(format="tflite", int8=True, data="/content/microbiologia-1/data.yaml")
```

Produce `best_int8.tflite`, que es el que consume la app. El notebook ademas lo
valida con `model_int8.val(...)`, o sea que la cuantizacion se verifico y no se
dio por buena.
