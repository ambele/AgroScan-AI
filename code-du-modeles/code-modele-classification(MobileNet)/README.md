# AgroScanAI — Détection des Maladies des Plantes
## Fine-tuning MobileNetV3 avec PlantVillage & PlantWild

AgroScanAI est un modèle de deep learning pour la détection automatique des maladies des plantes, entraîné en deux phases :

1. **PlantVillage** (54 303 images, 38 classes) — pré-entraînement en conditions de laboratoire.  
2. **PlantWild** (≈89 classes, in-the-wild) — fine-tuning sur des images terrain avec split officiel.

---

## Structure du projet

```
AgroScan-AI/
├── config_pv.yaml     # Config PlantVillage (pré-entraînement)
├── config_pw.yaml     # Config PlantWild (fine-tuning in-the-wild)
├── eda.py             # Exploration des données (optionnel)
├── pipeline.py        # Chargement données, augmentations, splits PV/PW
├── model.py           # MobileNetV3 + tête de classification + exports
├── train.py           # Entraînement, métriques, checkpoints, exports auto
├── export.py          # Export PTL / ONNX / Feature Extractor
├── robustness.py      # Temperature Scaling, RobustPredictor, robustesse
├── requirements.txt   # Dépendances Python
└── README.md          # Ce fichier
```

---

## Dataset PlantVillage

| Attribut | Détail                                          |
|----------|-------------------------------------------------|
| Volume   | 54 303 images / 38 classes                      |
| Cultures | 14 espèces (Tomate, Pomme, Maïs, etc.)          |
| Pipeline | Partitionnement 75/15/10 · Resize 224×224 · Aug |
| Objectif | Pré-entraînement du backbone en conditions lab  |

Structure de dossiers attendue :

```
data/PlantVillage/
    Tomato___Bacterial_spot/
        image001.jpg
        ...
    Tomato___healthy/
        image002.jpg
        ...
    Apple___Apple_scab/
        ...
```

---

## Dataset PlantWild

PlantWild est utilisé pour la phase de fine-tuning in-the-wild, avec split officiel.

| Attribut | Détail                                             |
|----------|----------------------------------------------------|
| Volume   | ≈89 classes (selon version du dataset)             |
| Pipeline | Split officiel via fichier `trainval`              |
| Fichiers | `images/`, `classes`, `trainval`                   |
| Objectif | Adapter le modèle aux conditions terrain réelles   |

Structure attendue :

```
data/PlantWild/
    images/
        maple tar spot/...
        peach leaf/...
        ...
    classes          # liste des classes (une par ligne)
    trainval         # lignes: <rel_path>=<class_id>=<split_id>
```

---

## Installation

### 1. Créer un environnement virtuel

```bash
python -m venv venv
source venv/bin/activate        # Linux / macOS
venv\Scriptsctivate           # Windows
```

### 2. Installer les dépendances

```bash
pip install -r requirements.txt
```

Deux fichiers de config principaux :

- `config_pv.yaml` pour PlantVillage (pré-entraînement),
- `config_pw.yaml` pour PlantWild (fine-tuning in-the-wild).

---

## Configurations

### 1. PlantVillage (`config_pv.yaml`)

| Section   | Paramètre                  | Valeur par défaut           | Description                                  |
|-----------|----------------------------|-----------------------------|----------------------------------------------|
| dataset   | root_dir                   | `./data/PlantVillage`       | Répertoire racine des images                 |
| dataset   | num_classes                | `38`                        | Nombre de classes                            |
| image     | input_size                 | `[224, 224]`                | Taille d’entrée du modèle                    |
| model     | backbone                   | `mobilenet_v3_large`        | Backbone (large)                             |
| model     | pretrained                 | `true`                      | Pré-entraînement ImageNet                    |
| model     | freeze_backbone_epochs     | `7`                         | Époques de gel du backbone (Phase 1)         |
| training  | epochs                     | `100`                       | Nombre total d’époques                       |
| training  | batch_size                 | `32`                        | Taille de batch                              |
| training  | learning_rate              | `0.0002`                    | LR de la tête                                |
| training  | optimizer                  | `adamw`                     | `adam`, `adamw` ou `sgd`                     |
| training  | scheduler                  | `cosine`                    | Warmup 5 epochs + cosine decay               |
| training  | mixed_precision            | `true`                      | AMP (Automatic Mixed Precision)              |
| training  | num_workers                | `2`                         | Workers DataLoader                           |
| metrics   | averaging                  | `macro`                     | Moyenne macro (adaptée au déséquilibre PV)   |

### 2. PlantWild (`config_pw.yaml`)

| Section   | Paramètre                  | Valeur par défaut               | Description                                  |
|-----------|----------------------------|---------------------------------|----------------------------------------------|
| dataset   | root_dir                   | `./data/PlantWild/images`       | Dossier des images PlantWild                 |
| dataset   | num_classes                | `89`                            | À ajuster au nb de lignes dans `classes`     |
| dataset   | class_names_file           | `./data/PlantWild/classes`      | Fichier officiel des classes PlantWild       |
| dataset   | use_official_split         | `true`                          | Active le split officiel `trainval`          |
| image     | input_size                 | `[224, 224]`                    | Taille d’entrée                              |
| model     | backbone                   | `mobilenet_v3_large`            | Même backbone que PV                         |
| model     | pretrained                 | `false`                         | Reprise via `--resume` (checkpoint PV)       |
| model     | freeze_backbone_epochs     | `5`                             | Durée de gel du backbone en PW               |
| training  | epochs                     | `60`                            | Nombre d’époques PlantWild                   |
| training  | batch_size                 | `32`                            | Taille de batch                              |
| training  | learning_rate              | `0.00015`                       | LR de la tête PlantWild                      |
| training  | optimizer                  | `adamw`                         |                                              |
| training  | scheduler                  | `cosine`                        | Warmup + cosine                              |
| training  | mixed_precision            | `true`                          |                                              |
| metrics   | averaging                  | `macro`                         |                                              |

---

## Lancement de l'entraînement

### Phase 1 — PlantVillage (pré-entraînement)

```bash
python train.py --config config_pv.yaml --data ./data/PlantVillage
```

Le meilleur checkpoint est sauvegardé ici :

```text
/content/drive/MyDrive/AgroScanAI/checkpoints/plantvillage/best_model.pth
```

### Phase 2 — PlantWild (fine-tuning in-the-wild)

```bash
python train.py   --config config_pw.yaml   --data ./data/PlantWild/images   --resume "/content/drive/MyDrive/AgroScanAI/checkpoints/plantvillage/best_model.pth"
```

- `--resume` recharge le backbone entraîné sur PlantVillage (chargement non strict : seules les couches compatibles sont chargées).
- La tête PlantWild (89 classes) est initialisée pour ce dataset et entraînée avec le split officiel (`trainval`).

### Options disponibles

```
--config      Chemin vers le fichier de config (pv/pw)
--data        Surcharge du répertoire de données
--resume      Reprendre depuis un checkpoint (.pth)
--eval-only   Évaluation uniquement (nécessite --resume)
```

Exemple d’évaluation sur PlantWild :

```bash
python train.py   --config config_pw.yaml   --data ./data/PlantWild/images   --eval-only   --resume "/content/drive/MyDrive/AgroScanAI/checkpoints/plantwild/best_model.pth"
```

---

## Export du modèle

L’export est lancé **automatiquement** à la fin de `train.py` (phase PlantWild).  
Il peut aussi être exécuté manuellement :

```bash
python export.py   --checkpoint "/content/drive/MyDrive/AgroScanAI/checkpoints/plantwild/best_model.pth"   --config config_pw.yaml   --output-dir "/content/drive/MyDrive/AgroScanAI/exports/plantwild"
```

Formats générés :

| Fichier                            | Format        | Usage                            |
|------------------------------------|---------------|----------------------------------|
| `model.onnx`                      | ONNX opset 13 | Interopérabilité, validation     |
| `plant_disease_classifier.tflite` | TFLite INT8   | Déploiement Android / Edge       |
| `feature_extractor.pt`            | PyTorch       | Extraction de features / RAG     |
| `model_gradcam.ptl`               | PTL Mobile    | Grad-CAM Android (double sortie) |

---

## Architecture du modèle

```
Input (B, 3, 224, 224)
        │
        ▼
MobileNetV3-Large (backbone pré-entraîné ImageNet pour PV)
  └── features (briques InvertedResidual)
  └── avgpool (AdaptiveAvgPool2d)
        │
        ▼  flatten → (B, 960)
        │
        ▼
Tête de classification personnalisée
  Linear(960 → 512)
  LayerNorm(512)
  ReLU()
  Dropout(0.35)
  Linear(512 → 256)
  LayerNorm(256)
  ReLU()
  Dropout(0.35)
  Linear(256 → N_classes)   # 38 pour PV, 89 pour PW
        │
        ▼
Logits (B, N_classes)
```

### Stratégie de fine-tuning en 2 phases

1. **Phase 1 (PV)**  
   - Backbone gelé pendant `freeze_backbone_epochs` (ex. 7).  
   - Seule la tête est entraînée.  
   - Scheduler : warmup linéaire (5 époques) + cosine decay.

2. **Phase 2 (PV & PW)**  
   - Les 4 dernières couches du backbone sont dégelées avec LR × `backbone_lr_factor` (ex. 0.05).  
   - Scheduler recréé avec les époques restantes.  
   - Phase PV améliore le backbone sur données lab, phase PW l’adapte au terrain.

---

## Métriques

Trois métriques sont calculées à chaque époque sur train, validation et test :

| Métrique   | Description                                   | Averaging |
|-----------|-----------------------------------------------|-----------|
| Accuracy  | Proportion de prédictions correctes           | —         |
| Recall    | Sensibilité par classe                        | `macro`   |
| F1-Score  | Moyenne harmonique précision / recall         | `macro`   |

- Averaging `macro` : chaque classe a le même poids, adapté au déséquilibre de PlantVillage et PlantWild.  
- La **CrossEntropyLoss pondérée** (poids calculés sur le train) compense le déséquilibre.  
- Le **F1-Score de validation** (`val_f1`) est la métrique de référence pour le checkpoint et l’early stopping.

Exemple de sortie console :

```text
Epoch 1/60 | 42.3s | Train Loss=2.1834 | Val Loss=1.9421 | Val F1=0.4688
✓ Best model : ./checkpoints/plantwild/best_model.pth
```

---

## Visualisation

### TensorBoard

```bash
tensorboard --logdir ./runs
```

Graphiques disponibles :

- `Loss/train_step`, `Loss/train`, `Loss/val`
- `Metrics/accuracy`, `Metrics/recall`, `Metrics/f1_score`
- `LR` (courbe du taux d’apprentissage)

### Weights & Biases

Activé automatiquement si `use_wandb: true` dans les configs.  
Inclut : courbes de métriques, confusion matrix interactive, classification report.

---

## Fichiers générés

```text
checkpoints/
    plantvillage/
        best_model.pth      # Meilleur checkpoint PV (val_f1)
        last_model.pth
    plantwild/
        best_model.pth      # Meilleur checkpoint PW
        last_model.pth

exports/
    plantwild/
        model.onnx
        plant_disease_classifier.tflite
        feature_extractor.pt
        model_gradcam.ptl
        temperature.pt      # Paramètre de calibration (TemperatureScaling)

logs/
    plantvillage/
        training_history.json
        confusion_matrix.png
    plantwild/
        training_history.json
        confusion_matrix.png

runs/
    ...                    # Données TensorBoard

data/
    PlantVillage/
        ...
    PlantWild/
        images/
        classes
        trainval
```

---

## Test rapide du pipeline

Test des DataLoaders :

```bash
python pipeline.py --config config_pv.yaml --data ./data/PlantVillage
python pipeline.py --config config_pw.yaml --data ./data/PlantWild/images
```

Test rapide du modèle (sans données) :

```bash
python model.py
```

---

## Pipeline d’export complet

```text
PyTorch train.py (config_pw.yaml)
      │
      ▼
plantwild/best_model.pth
      │
      ├──► ONNX (opset 13)
      │         │
      │         ├──► onnx2tf → SavedModel → TFLite INT8
      │         └──► ONNXRuntime (inférence serveur)
      │
      ├──► Feature Extractor (.pt)
      └──► PTL Mobile (.ptl)   ← Grad-CAM Android
```
