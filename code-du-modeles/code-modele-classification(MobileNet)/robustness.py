#!/usr/bin/env python3
"""
AgroScan AI - Robustification Terrain
CHANGEMENTS vs version précédente :
  1. build_mixed_dataset : ImageFolder → PlantVillageDataset (cohérence pipeline.py)
  2. build_train_transforms_robust : supprimé (doublon pipeline.py)
  3. evaluate_robustness : gaussian_blur signature corrigée PyTorch 2.x
  4. RobustPredictor : normalisation ImageNet ajoutée + threshold 0.65 → 0.75
  5. build_mixed_dataset : plafonnement PlantDoc à 10% de PV
  6. TemperatureScaling : source de vérité unique (train.py doit l'importer ici)
"""

import os
import random
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader, ConcatDataset, Subset
from torchvision import transforms
from torchvision.datasets import ImageFolder
from PIL import Image


# ============================================================
# 1. AUGMENTATIONS TERRAIN
# (JPEGCompression et RandomShadow restent ici comme référence
#  mais sont importées depuis pipeline.py dans le projet)
# ============================================================

class JPEGCompression:
    """Simule la compression JPEG d'un smartphone."""
    def __init__(self, quality_range=(60, 95)):
        self.quality_range = quality_range

    def __call__(self, img: Image.Image) -> Image.Image:
        import io
        quality = random.randint(*self.quality_range)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=quality)
        buf.seek(0)
        return Image.open(buf).copy()


class RandomShadow:
    """Ajoute une ombre aléatoire (contre-jour terrain)."""
    def __init__(self, prob=0.25):
        self.prob = prob

    def __call__(self, img: Image.Image) -> Image.Image:
        if random.random() > self.prob:
            return img
        from PIL import ImageDraw
        img = img.copy()
        w, h = img.size
        shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        draw   = ImageDraw.Draw(shadow)
        points = [
            (random.randint(0, w), 0),
            (random.randint(0, w), h),
            (random.randint(0, w), h),
            (random.randint(0, w), 0),
        ]
        alpha = random.randint(40, 100)
        draw.polygon(points, fill=(0, 0, 0, alpha))
        img = img.convert("RGBA")
        img = Image.alpha_composite(img, shadow)
        return img.convert("RGB")


# ============================================================
# 2. DATASET MIXTE PlantVillage + PlantDoc
# ============================================================

def build_mixed_dataset(cfg: dict, transform) -> ConcatDataset:
    """
    Combine PlantVillage (lab) + PlantDoc (terrain).
    FIX : utilise PlantVillageDataset (structure plate) au lieu de ImageFolder.
    FIX : PlantDoc plafonné à 10% de PV pour éviter déstabilisation.

    Dans Colab :
        !git clone https://github.com/pratikkayal/PlantDoc-Dataset ./data/PlantDoc
    """
    from pipeline import PlantVillageDataset   # FIX: import explicite

    pv_root   = cfg["dataset"]["root_dir"]
    pdoc_root = cfg["dataset"].get("plantdoc_dir", "./data/PlantDoc")

    pv_dataset = PlantVillageDataset(pv_root, transform=transform)  # FIX
    datasets   = [pv_dataset]

    if os.path.exists(pdoc_root):
        pdoc_dataset = ImageFolder(root=pdoc_root, transform=transform)
        # FIX : plafonner à 10% de PV (évite surreprésentation)
        max_pdoc     = len(pv_dataset) // 10
        pdoc_indices = (list(range(len(pdoc_dataset))) * 3)[:max_pdoc]
        pdoc_weighted = Subset(pdoc_dataset, pdoc_indices)
        datasets.append(pdoc_weighted)
        print(
            f"[Dataset] PlantVillage : {len(pv_dataset)} | "
            f"PlantDoc (plafonné) : {len(pdoc_weighted)}"
        )
    else:
        print(f"[Dataset] PlantDoc absent → PlantVillage uniquement ({len(pv_dataset)} images)")

    return ConcatDataset(datasets) if len(datasets) > 1 else pv_dataset


# ============================================================
# 3. TEMPERATURE SCALING — source de vérité unique
# ============================================================

class TemperatureScaling(nn.Module):
    """
    Calibration post-entraînement.
    FIX : source de vérité unique — train.py doit importer cette classe.

    Utilisation dans train.py (_auto_export) :
        from robustness import TemperatureScaling
        calibrator = TemperatureScaling(model)
        calibrator.calibrate(val_loader, device=str(device))
        calibrator.save(os.path.join(export_dir, "temperature.pt"))
    """
    def __init__(self, model: nn.Module):
        super().__init__()
        self.model       = model
        self.temperature = nn.Parameter(torch.ones(1) * 1.0)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.model(x) / self.temperature

    def calibrate(self, val_loader: DataLoader, device: str = "cuda") -> float:
        self.model.eval()
        self.to(device)
        optimizer = torch.optim.LBFGS([self.temperature], lr=0.01, max_iter=100)
        criterion = nn.CrossEntropyLoss()

        all_logits, all_labels = [], []
        with torch.no_grad():
            for images, labels in val_loader:
                all_logits.append(self.model(images.to(device)).cpu())
                all_labels.append(labels)

        all_logits = torch.cat(all_logits).to(device)
        all_labels = torch.cat(all_labels).to(device)

        def eval_step():
            optimizer.zero_grad()
            loss = criterion(all_logits / self.temperature, all_labels)
            loss.backward()
            return loss

        optimizer.step(eval_step)
        temp_val = self.temperature.item()
        print(f"[Calibration] Température optimale : {temp_val:.4f}")
        return temp_val

    def save(self, path: str):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        torch.save({"temperature": self.temperature.item()}, path)
        print(f"[Calibration] Sauvegardé : {path}")

    @staticmethod
    def load(path: str) -> float:
        return torch.load(path, map_location="cpu")["temperature"]


# ============================================================
# 4. INFÉRENCE ROBUSTE avec rejet OOD
# ============================================================

class RobustPredictor:
    """
    Inférence avec temperature scaling + rejet images ambiguës.
    FIX : normalisation ImageNet ajoutée (critique pour inférence Android)
    FIX : threshold 0.65 → 0.75 (adapté à 38 classes post-calibration)

    Utilisation :
        predictor = RobustPredictor(model, class_names, temperature=1.3)
        result = predictor.predict(image_tensor)   # tensor CHW [0,1] non normalisé
        if result["uncertain"]:
            # Afficher "Image peu claire" dans l'app Android
    """

    _IMAGENET_NORMALIZE = transforms.Normalize(
        mean=[0.485, 0.456, 0.406],
        std=[0.229, 0.224, 0.225],
    )

    def __init__(
        self,
        model: nn.Module,
        class_names: list,
        temperature: float          = 1.0,
        confidence_threshold: float = 0.75,  # FIX: 0.65 → 0.75
        top_k: int                  = 3,
        device: str                 = "cuda",
    ):
        self.model       = model.eval().to(device)
        self.class_names = class_names
        self.temperature = temperature
        self.threshold   = confidence_threshold
        self.top_k       = top_k
        self.device      = device

    @torch.no_grad()
    def predict(self, image_tensor: torch.Tensor) -> dict:
        # FIX : normalisation ImageNet (image_tensor = CHW float [0,1])
        image_tensor = self._IMAGENET_NORMALIZE(image_tensor)
        if image_tensor.dim() == 3:
            image_tensor = image_tensor.unsqueeze(0)

        logits = self.model(image_tensor.to(self.device))
        probs  = F.softmax(logits / self.temperature, dim=1)[0]

        top_probs, top_indices = torch.topk(probs, self.top_k)
        top_k_results = [
            {"class": self.class_names[i.item()], "confidence": round(p.item(), 4)}
            for p, i in zip(top_probs, top_indices)
        ]

        best_prob  = top_probs[0].item()
        best_class = self.class_names[top_indices[0].item()]
        uncertain  = best_prob < self.threshold

        return {
            "prediction": "Image peu claire — veuillez reprendre la photo"
                          if uncertain else best_class,
            "confidence": round(best_prob, 4),
            "uncertain":  uncertain,
            "top_k":      top_k_results,
        }


# ============================================================
# 5. ÉVALUATION ROBUSTESSE
# ============================================================

def evaluate_robustness(model, val_loader, device="cuda") -> dict:
    """
    Mesure la chute de performance sous différentes perturbations terrain.
    FIX : gaussian_blur → kernel_size=[5,5] (signature correcte PyTorch 2.x)
    """
    perturbations = {
        "clean":            lambda x: x,
        "gaussian_noise":   lambda x: torch.clamp(x + torch.randn_like(x) * 0.05, 0, 1),
        "motion_blur":      lambda x: transforms.functional.gaussian_blur(
                                x, kernel_size=[5, 5], sigma=2.0   # FIX
                            ),
        "brightness_shift": lambda x: torch.clamp(x * 1.4, 0, 1),
        "low_contrast":     lambda x: x * 0.6 + 0.2,
    }

    model.eval()
    results = {}
    for name, perturb in perturbations.items():
        correct, total = 0, 0
        with torch.no_grad():
            for images, labels in val_loader:
                images = perturb(images).to(device)
                preds  = model(images).argmax(dim=1)
                correct += (preds == labels.to(device)).sum().item()
                total   += labels.size(0)
        acc = round(correct / total, 4)
        results[name] = acc
        print(f"[Robustesse] {name:<20} Accuracy : {acc:.4f}")

    clean_acc = results["clean"]
    worst_acc = min(results[k] for k in results if k != "clean")
    drop = clean_acc - worst_acc
    print(f"\n[Robustesse] Chute max vs clean : -{drop:.4f}")
    return results