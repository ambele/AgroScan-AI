import torch
from torch.utils.data import DataLoader, ConcatDataset, WeightedRandomSampler, random_split
from torchvision.datasets import ImageFolder
from torchvision.transforms import v2
import math

def get_transforms(config, phase="phase_1", img_size=None):
    size = img_size if img_size else config['image']['input_size'][0]
    mean, std = config['image']['mean'], config['image']['std']
    
    train_tfm = [v2.Resize((size, size), antialias=True), v2.ToImage(), v2.ToDtype(torch.float32, scale=True)]
    
    if phase == "phase_1":
        aug_cfg = config['phase_1']['augmentations']
        if aug_cfg['random_horizontal_flip']: train_tfm.insert(1, v2.RandomHorizontalFlip())
        train_tfm.insert(1, v2.RandomRotation(aug_cfg['random_rotation_degrees']))
        
    elif phase == "phase_2":
        aug_cfg = config['phase_2']['augmentations']
        if aug_cfg['geometric']['horizontal_flip']: train_tfm.insert(1, v2.RandomHorizontalFlip())
        train_tfm.insert(1, v2.RandomRotation(aug_cfg['geometric']['rotation']))
        color_cfg = aug_cfg['color']['color_jitter']
        train_tfm.insert(1, v2.ColorJitter(brightness=color_cfg['brightness'], contrast=color_cfg['contrast'], saturation=color_cfg['saturation'], hue=color_cfg['hue']))
        train_tfm.insert(1, v2.RandomApply([v2.GaussianBlur(kernel_size=(5, 9))], p=aug_cfg['noise_realism']['gaussian_blur_prob']))
        train_tfm.append(v2.RandomErasing(p=aug_cfg['noise_realism']['random_erasing_prob']))
    
    val_tfm = [v2.Resize((size, size), antialias=True), v2.ToImage(), v2.ToDtype(torch.float32, scale=True), v2.Normalize(mean=mean, std=std)]
    train_tfm.append(v2.Normalize(mean=mean, std=std))
    
    return v2.Compose(train_tfm), v2.Compose(val_tfm)

def create_splits(dataset, train_ratio, val_ratio, seed):
    total = len(dataset)
    train_len = int(total * train_ratio)
    val_len = int(total * val_ratio)
    test_len = total - train_len - val_len
    generator = torch.Generator().manual_seed(seed)
    return random_split(dataset, [train_len, val_len, test_len], generator=generator)

def get_dataloaders(config, phase="phase_1", dynamic_img_size=None, dynamic_batch_size=None):
    train_tfm, val_tfm = get_transforms(config, phase, dynamic_img_size)
    
    sources = config['dataset']['sources']
    train_ratio, val_ratio = config['dataset']['splits']['train'], config['dataset']['splits']['val']
    seed = config['project']['seed']
    
    # Chargement et split individuel (garantit que chaque source est présente en val)
    pv_ds = ImageFolder(sources['plantvillage']['path'], transform=train_tfm)
    pv_train, pv_val, pv_test = create_splits(pv_ds, train_ratio, val_ratio, seed)
    pv_val.dataset.transform = val_tfm # Applique val_tfm au subset de validation
    
    pd_ds = ImageFolder(sources['plantdoc']['path'], transform=train_tfm)
    pd_train, pd_val, pd_test = create_splits(pd_ds, train_ratio, val_ratio, seed)
    pd_val.dataset.transform = val_tfm
    
    pp_ds = ImageFolder(sources['plant_pathology']['path'], transform=train_tfm)
    pp_train, pp_val, pp_test = create_splits(pp_ds, train_ratio, val_ratio, seed)
    pp_val.dataset.transform = val_tfm

    batch_size = dynamic_batch_size if dynamic_batch_size else config[phase]['batch_size']
    num_workers = config['training']['num_workers']
    
    if phase == "phase_1":
        # Phase 1: Uniquement PlantVillage
        train_loader = DataLoader(pv_train, batch_size=batch_size, shuffle=True, num_workers=num_workers, pin_memory=True)
        val_loader = DataLoader(pv_val, batch_size=batch_size, shuffle=False, num_workers=num_workers, pin_memory=True)
    
    else:
        # Phase 2: Toutes les sources fusionnées
        full_train = ConcatDataset([pv_train, pd_train, pp_train])
        full_val = ConcatDataset([pv_val, pd_val, pp_val])
        
        # Poids dynamiques pour l'entraînement
        w_cfg = config['phase_2']['dataset_weights']
        w_list = [w_cfg['plantvillage'], w_cfg['plantdoc'], w_cfg['plant_pathology']]
        lens = [len(pv_train), len(pd_train), len(pp_train)]
        
        sample_weights = []
        for weight, length in zip(w_list, lens):
            sample_weights.extend([weight / length] * length)
            
        sampler = WeightedRandomSampler(weights=sample_weights, num_samples=len(full_train), replacement=True)
        
        train_loader = DataLoader(full_train, batch_size=batch_size, sampler=sampler, num_workers=num_workers, pin_memory=True)
        val_loader = DataLoader(full_val, batch_size=batch_size, shuffle=False, num_workers=num_workers, pin_memory=True)
        
    return train_loader, val_loader