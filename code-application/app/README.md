# AgroScanAI (Android)
Diagnostic visuel de maladies des plantes sur smartphone (Edge AI)

## 1. Description

AgroScanAI est une application Android native qui permet de diagnostiquer visuellement des maladies des plantes à partir d’une simple photo de feuille.  
Toute l’inférence est réalisée **en local sur l’appareil** à l’aide de modèles embarqués (YOLOv8n au format TFLite et un modèle PTL pour la classification + Grad‑CAM), sans envoi d’images vers le cloud.

Fonctionnalités principales :

- Capture d’image via CameraX ou sélection dans la galerie.
- Détection de la feuille (YOLOv8n) et determination de la plante, recadrage automatique de la région d’intérêt.
- Classification de la maladie par un modèle MobileNetV3 exporté en PTL double sorties (`model_gradcam.ptl`).
- Affichage d’un score de confiance.
- Visualisation d’une carte de chaleur Grad‑CAM pour l’explicabilité.
- Fiches maladies (symptômes + recommandations) chargées depuis un fichier JSON embarqué.

Ce README explique comment **ouvrir, lancer et tester l’application sur Android**.

---

## 2. Prérequis

- Android Studio (Giraffe ou version plus récente).
- JDK 17 (ou version recommandée par ta version d’Android Studio).
- SDK Android : API 26+
- Un appareil Android physique
- Les fichiers de modèles et de métadonnées déjà présents dans `app/src/main/assets/` :
    - `yolov8n_species.tflite` : détection de feuilles (YOLOv8n).
    - `model_gradcam.ptl` : classification MobileNetV3 + activations Grad‑CAM.
    - `etiquettes.txt` : liste des classes (espèce + maladie).
    - `etiquettes_symptomes_recommandations.json` : fiches maladies (symptômes et recommandations).
    - `temperature.txt` : fichier de configuration (Lsert à stocker le paramètre de température utilisé pour calibrer les probabilités du modèle de classification).

---

## 3. Structure du projet Android

Le code Android se trouve dans le module `app` :

```text
app/
 ├── manifests/
 │    └── AndroidManifest.xml
 ├── kotlin+java/
 │    └── com.example.agroscan_app/
 │         ├── CameraPreview.kt
 │         ├── DiagnosticDisclaimerBanner.kt
 │         ├── DiseaseDetailScreen.kt
 │         ├── MainActivity.kt
 │         ├── PlantClassifier.kt
 │         ├── ResultScreen.kt
 │         ├── ScannerScreen.kt
 │         ├── ScanResultsSummaryScreen.kt
 │         └── SplashScreen.kt
 ├── assets/
 │    ├── etiquettes.txt
 │    ├── etiquettes_symptomes_recommandations.json
 │    ├── model_gradcam.ptl
 │    ├── temperature.txt
 │    └── yolov8n_species.tflite
 └── res/
      ├── layout/...
      ├── values/...
      └── drawable/...
```

Rôle des fichiers principaux :

- `MainActivity.kt` : point d’entrée de l’application, configuration de la navigation (Jetpack Compose).
- `SplashScreen.kt` : écran de lancement (chargement des modèles et initialisation).
- `ScannerScreen.kt` / `CameraPreview.kt` : capture de la feuille (CameraX), interaction avec l’utilisateur.
- `PlantClassifier.kt` : chargement des modèles (`yolov8n_species.tflite`, `model_gradcam.ptl`), pipeline de détection + classification, calcul Grad‑CAM.
- `ResultScreen.kt` / `ScanResultsSummaryScreen.kt` : affichage du diagnostic (classe, score de confiance, heatmap).
- `DiseaseDetailScreen.kt` : affichage de la fiche maladie (symptômes, recommandations) à partir de `etiquettes_symptomes_recommandations.json`.
- `DiagnosticDisclaimerBanner.kt` : bannière rappelant les limites légales et le rôle d’aide au diagnostic.

Les fichiers dans `assets/` sont chargés au runtime, ce qui permet à l’application de fonctionner entièrement hors‑ligne.

---

## 4. Ouverture du projet dans Android Studio

1. Lancer **Android Studio**.
2. Sélectionner `File > Open...`.
3. Choisir le dossier `android/` du projet (là où se trouve le module `app`).
4. Valider et laisser Android Studio télécharger les dépendances Gradle.
5. Vérifier que la configuration de lancement sélectionnée est bien `app`.

---

## 5. Déploiement sur smartphone physique

1. Activer les **Options développeur** et le **Débogage USB** sur le smartphone.
2. Connecter le smartphone au PC via USB.
3. Accepter la clé de débogage sur le téléphone si Android le demande.
4. Dans Android Studio, sélectionner le smartphone comme cible.
5. Cliquer sur **Run ▶** pour compiler et installer l’APK.

L’application AgroScanAI est alors installée et démarrée automatiquement.

---

## 6. Distribution et installation de l’APK

En plus du projet Android Studio, une version compilée de l’application est fournie sous forme d’APK, par exemple `AgroScanAI-v1.apk`.

Procédure d’installation :

1. Copier `AgroScanAI-v1.apk` sur un smartphone Android (via câble USB).
2. Sur le téléphone, autoriser l’installation d’applications de sources inconnues (chemin variable selon la version d’Android : typiquement Réglages > Sécurité > Installer applications inconnues).
3. Ouvrir le fichier `AgroScanAI-v1.apk` depuis le gestionnaire de fichiers et confirmer l’installation.
4. Lancer l’application **AgroScanAI** depuis le lanceur d’applications.

L’ensemble des modèles et fichiers nécessaires étant embarqués dans l’APK et dans le dossier `assets/`, l’application fonctionne ensuite entièrement hors‑ligne.

---

## 7. Scénario de test recommandé

Ce scénario permet de vérifier rapidement le bon fonctionnement de bout en bout.

1. **Lancer AgroScanAI** sur le smartphone.
2. À la première utilisation :
    - autoriser l’accès à la caméra,
    - autoriser l’accès au stockage si l’import depuis la galerie est utilisé.
3. Écran de **Splash** :
    - un écran de chargement s’affiche pendant l’initialisation des modèles (chargement des fichiers `.tflite` et `.ptl` depuis `assets/`).
4. Écran de **Scanner** :
    - cadrer une feuille dans le viseur (ou une image de test imprimée),
    - utiliser le bouton de capture pour prendre la photo,
    - alternativement, sélectionner une image existante depuis la galerie.
5. Pipeline de traitement :
    - la feuille est détectée via `yolov8n_species.tflite` et recadrée,
    - la région recadrée est transmise à `model_gradcam.ptl` pour la classification,
    - les activations sont utilisées pour générer une carte Grad‑CAM.
6. Écran de **Résultat** :
    - affichage de l’espèce et de la maladie probable,
    - score de confiance (en %),
    - superposition de la heatmap Grad‑CAM sur la feuille,
    - bannière de disclaimer (DiagnosticDisclaimerBanner) rappelant qu’il s’agit d’une aide au diagnostic.
7. Écran de **Détails maladie** :
    - via un bouton dédié, accès à la fiche maladie issue de `etiquettes_symptomes_recommandations.json` :
        - description des symptômes,
        - recommandations d’actions (bonnes pratiques)

---

## 8. Dépannage

- **L’application plante au démarrage** :
    - vérifier que les fichiers dans `app/src/main/assets/` existent bien :
        - `yolov8n_species.tflite`
        - `model_gradcam.ptl`
        - `etiquettes.txt`
        - `etiquettes_symptomes_recommandations.json`
- **Écran noir ou crash lors de la capture** :
    - vérifier que les permissions caméra et stockage sont accordées,
    - tester sur un smartphone physique
- **Diagnostic très lent** :
    - tester sur un appareil plus récent,
    - fermer les autres applications en arrière‑plan.
- **Pas de fiche maladie ou texte manquant** :
    - vérifier l’intégrité de `etiquettes.txt` et `etiquettes_symptomes_recommandations.json`.

---

## 9. Limitations

- L’application est un **prototype académique** : elle ne remplace pas un diagnostic agronomique officiel.
- Les modèles ont été entraînés sur des jeux de données publics (PlantVillage, PlantDoc et Plat Pathology.) et peuvent être moins précis sur des conditions extrêmement différentes (co‑infections, conditions lumineuses extrêmes, nouvelles variétés).
- Certaines fonctionnalités dépendent des performances matérielles du smartphone (CPU/GPU).

---

## 10. Licence et contexte

AgroScanAI a été développé dans le cadre du cours **8INF934 – Atelier pratique en intelligence artificielle I** à l’Université du Québec à Chicoutimi (UQAC), session Été 2026.  
Le code et les modèles sont fournis à des fins de démonstration et d’évaluation académique.