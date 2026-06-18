import yaml
import torch
import torch.nn as nn
import os
from torchvision import models

def ensure_class_file_exists(config):
    """
    Génère automatiquement ./data/class_names.txt en lisant les sous-dossiers
    de la source PlantVillage si le fichier est manquant.
    """
    file_path = config['dataset']['classes']['file']
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    
    if not os.path.exists(file_path):
        print(f"⚠️ {file_path} introuvable. Génération automatique basée sur PlantVillage...")
        source_path = config['dataset']['sources']['plantvillage']['path']
        
        # Récupération des dossiers (classes) et tri alphabétique pour garantir la cohérence des indices
        try:
            classes = sorted([d for d in os.listdir(source_path) if os.path.isdir(os.path.join(source_path, d))])
            with open(file_path, 'w') as f:
                for class_name in classes:
                    f.write(f"{class_name}\n")
            print(f"✅ Fichier {file_path} généré avec {len(classes)} classes.")
        except FileNotFoundError:
            print(f"❌ Erreur : Le chemin source {source_path} n'existe pas. Vérifiez votre config.yaml.")
            raise

def load_classes(config_path="config.yaml"):
    """Charge la liste des classes depuis le fichier spécifié dans la config."""
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    
    # Vérification de l'existence du fichier de classes
    ensure_class_file_exists(config)
    
    if config['dataset']['classes']['strategy'] == 'from_file':
        class_file = config['dataset']['classes']['file']
        with open(class_file, 'r') as f:
            class_names = [line.strip() for line in f.readlines() if line.strip()]
        
        # On force le tri pour s'assurer que l'indice 0 correspond toujours au même dossier
        return sorted(class_names)
    else:
        raise ValueError("Stratégie de classe non reconnue : utilisez 'from_file'.")

def build_model(config):
    """
    Construit MobileNetV3-Large avec une tête de classification adaptée
    aux 38 classes d'AgroScan-AI.
    """
    num_classes = config['dataset']['num_classes']
    dropout_rate = config['model']['dropout']
    hidden_dims = config['model']['classifier_hidden_dims'] # ex: [512, 256]
    
    # Chargement du modèle avec poids pré-entraînés ImageNet si activé
    weights = models.MobileNet_V3_Large_Weights.DEFAULT if config['model']['pretrained'] else None
    model = models.mobilenet_v3_large(weights=weights)
    
    # Remplacement de la couche classifier par notre tête personnalisée
    in_features = model.classifier[0].in_features
    
    model.classifier = nn.Sequential(
        nn.Linear(in_features, hidden_dims[0]),
        nn.Hardswish(inplace=True),
        nn.Dropout(p=dropout_rate, inplace=True),
        nn.Linear(hidden_dims[0], hidden_dims[1]),
        nn.Hardswish(inplace=True),
        nn.Dropout(p=dropout_rate, inplace=True),
        nn.Linear(hidden_dims[1], num_classes)
    )
    
    return model