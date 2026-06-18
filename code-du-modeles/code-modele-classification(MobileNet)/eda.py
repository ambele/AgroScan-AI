"""
Script d'Analyse Exploratoire des Données (EDA) - PlantVillage
Analyse des distributions de classes, des dimensions, des formats et des canaux.
"""

import os
from pathlib import Path
from collections import Counter
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from PIL import Image
from tqdm import tqdm

# Configuration du style des graphiques
sns.set_theme(style="whitegrid")
plt.rcParams.update({'font.size': 11, 'axes.labelsize': 12, 'axes.titlesize': 14})


class PlantVillageEDA:
    def __init__(self, root_dir: str):
        self.root_dir = Path(root_dir)
        self.extensions = {".jpg", ".jpeg", ".png", ".bmp", ".tiff"}
        self.df_metadata = None

        if not self.root_dir.exists():
            raise FileNotFoundError(f"Le répertoire {self.root_dir} n'existe pas.")

    def collect_metadata(self) -> pd.DataFrame:
        """Parcourt le dossier pour extraire les métadonnées de chaque image."""
        print(f"[EDA] Analyse du répertoire : {self.root_dir}")
        data = []
        
        # Récupération des sous-dossiers (classes)
        classes = [d for d in self.root_dir.iterdir() if d.is_dir()]
        
        if not classes:
            print("[Warning] Aucun sous-dossier trouvé. Les images sont-elles directement à la racine ?")
            return pd.DataFrame()

        for class_dir in classes:
            class_name = class_dir.name
            # Filtrage des images par extension
            img_paths = [p for p in class_dir.iterdir() if p.suffix.lower() in self.extensions]
            
            print(f"  -> Extraction des métadonnées pour la classe : '{class_name}' ({len(img_paths)} images)")
            
            for path in tqdm(img_paths, desc=f"Classe: {class_name[:20]}", leave=False):
                try:
                    with Image.open(path) as img:
                        width, height = img.size
                        mode = img.mode  # RGB, Grayscale, etc.
                        format_ = img.format
                        
                        data.append({
                            "path": str(path),
                            "class": class_name,
                            "width": width,
                            "height": height,
                            "mode": mode,
                            "format": format_,
                            "ratio": width / height,
                            "size_kb": path.stat().st_size / 1024
                        })
                except Exception as e:
                    # Permet de repérer les fichiers corrompus dès l'EDA
                    print(f"\n[Erreur] Impossible de lire {path.name} : {e}")

        self.df_metadata = pd.DataFrame(data)
        return self.df_metadata

    def print_summary_statistics(self):
        """Affiche un résumé textuel des statistiques."""
        if self.df_metadata is None or self.df_metadata.empty:
            print("Aucune donnée à analyser. Exécutez d'abord 'collect_metadata()'.")
            return

        print("\n" + "="*50)
        print("          RÉSUMÉ DES STATISTIQUES EDA          ")
        print("="*50)
        print(f"Nombre total d'images valides : {len(self.df_metadata)}")
        print(f"Nombre total de classes       : {self.df_metadata['class'].nunique()}")
        
        print("\n--- Distribution des Formats d'Images ---")
        print(self.df_metadata['format'].value_counts(normalize=True) * 100)
        
        print("\n--- Distribution des Modes (Canaux) ---")
        print(self.df_metadata['mode'].value_counts())
        
        print("\n--- Statistiques sur les Dimensions ---")
        print(self.df_metadata[['width', 'height', 'size_kb']].describe().loc[['min', 'mean', 'max']])
        
        # Détection du déséquilibre de classe
        counts = self.df_metadata['class'].value_counts()
        print("\n--- Équilibre des Classes ---")
        print(f"Classe majoritaire : '{counts.index[0]}' avec {counts.iloc[0]} images")
        print(f"Classe minoritaire : '{counts.index[-1]}' avec {counts.iloc[-1]} images")
        print(f"Ratio de déséquilibre (Max/Min) : {counts.iloc[0] / counts.iloc[-1]:.2f}")

    def plot_class_distribution(self, output_path: str = "eda_class_distribution.png"):
        """Génère un graphique en barres de la distribution des classes."""
        plt.figure(figsize=(12, 6))
        order = self.df_metadata['class'].value_counts().index
        sns.countplot(data=self.df_metadata, y='class', order=order, palette="viridis")
        plt.title("Distribution du nombre d'images par classe")
        plt.xlabel("Nombre d'images")
        plt.ylabel("Classes")
        plt.tight_layout()
        plt.savefig(output_path, dpi=300)
        plt.close()
        print(f"[EDA] Graphique de distribution sauvegardé : {output_path}")

    def plot_dimension_analysis(self, output_path: str = "eda_dimensions.png"):
        """Analyse la dispersion des hauteurs et largeurs des images."""
        plt.figure(figsize=(10, 5))
        
        # JointPlot ou ScatterPlot pour voir si toutes les images ont la même taille (ex: 256x256 originel)
        sns.scatterplot(data=self.df_metadata, x='width', y='height', alpha=0.6, edgecolor=None, color='teal')
        plt.title("Dispersion des dimensions des images (Largeur vs Hauteur)")
        plt.xlabel("Largeur (pixels)")
        plt.ylabel("Hauteur (pixels)")
        
        # Ajout d'une ligne d'identité pour le ratio 1:1
        max_val = max(self.df_metadata['width'].max(), self.df_metadata['height'].max())
        plt.plot([0, max_val], [0, max_val], color='red', linestyle='--', label='Ratio 1:1')
        plt.legend()
        
        plt.tight_layout()
        plt.savefig(output_path, dpi=300)
        plt.close()
        print(f"[EDA] Graphique des dimensions sauvegardé : {output_path}")

    def plot_sample_images(self, num_samples_per_class: int = 2, output_path: str = "eda_samples.png"):
        """Génère une grille d'exemples d'images pour inspection visuelle humaine."""
        classes = self.df_metadata['class'].unique()
        num_classes = len(classes)
        
        if num_classes == 0:
            return

        fig, axes = plt.subplots(num_classes, num_samples_per_class, figsize=(num_samples_per_class * 3, num_classes * 3))
        
        # Gérer le cas où il n'y a qu'une seule classe ou un seul échantillon
        if num_classes == 1:
            axes = np.expand_dims(axes, axis=0)
        if num_samples_per_class == 1:
            axes = np.expand_dims(axes, axis=1)

        for i, cls in enumerate(classes):
            df_cls = self.df_metadata[self.df_metadata['class'] == cls]
            samples = df_cls.sample(n=min(num_samples_per_class, len(df_cls)), random_state=42)['path'].values
            
            for j in range(num_samples_per_class):
                ax = axes[i, j]
                if j < len(samples):
                    img = Image.open(samples[j])
                    ax.imshow(img)
                    if j == 0:
                        ax.set_ylabel(cls[:15] + "...", rotation=0, labelpad=40, va='center', fontweight='bold')
                ax.set_xticks([])
                ax.set_yticks([])
                
        plt.suptitle("Échantillons d'images par classe (PlantVillage)", fontsize=16, y=0.99)
        plt.tight_layout()
        plt.savefig(output_path, dpi=200)
        plt.close()
        print(f"[EDA] Grille d'échantillons sauvegardée : {output_path}")

    def run_all(self):
        """Exécute l'analyse complète."""
        self.collect_metadata()
        if not self.df_metadata.empty:
            self.print_summary_statistics()
            self.plot_class_distribution()
            self.plot_dimension_analysis()
            # Si vous avez beaucoup de classes (>15), réduisez ou désactivez la grille d'échantillons
            if self.df_metadata['class'].nunique() <= 15:
                self.plot_sample_images()
        else:
            print("[EDA] Échec : Aucune donnée trouvée.")


# ─────────────────────────────────────────────
# CLI Exécution
# ─────────────────────────────────────────────

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="EDA pour le dataset PlantVillage")
    parser.add_argument("--data", required=True, help="Chemin vers le dossier racine des images (ex: data/PlantVillage)")
    args = parser.parse_args()

    # Lancement de l'analyse
    eda = PlantVillageEDA(root_dir=args.data)
    eda.run_all()