import os
import yaml
import torch
import torch.nn as nn
import torch.optim as optim
import argparse
import wandb
import shutil
from torch.utils.tensorboard import SummaryWriter
from tqdm import tqdm
from sklearn.metrics import f1_score, accuracy_score
import numpy as np

# Imports de vos modules personnalisés
from model import build_model
from pipeline import get_dataloaders

class EarlyStopping:
    def __init__(self, patience=12, min_delta=0.001, mode='max'):
        self.patience = patience
        self.min_delta = min_delta
        self.mode = mode
        self.counter = 0
        self.best_score = None
        self.early_stop = False

    def __call__(self, score):
        if self.best_score is None:
            self.best_score = score
        elif (self.mode == 'max' and score < self.best_score + self.min_delta) or \
             (self.mode == 'min' and score > self.best_score - self.min_delta):
            self.counter += 1
            if self.counter >= self.patience: self.early_stop = True
        else:
            self.best_score = score
            self.counter = 0

def validate(model, val_loader, criterion, device):
    model.eval()
    val_loss = 0.0
    all_preds, all_labels = [], []
    with torch.no_grad():
        for inputs, labels in val_loader:
            inputs, labels = inputs.to(device), labels.to(device)
            outputs = model(inputs)
            loss = criterion(outputs, labels)
            val_loss += loss.item() * inputs.size(0)
            
            _, preds = outputs.max(1)
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.cpu().numpy())
            
    avg_loss = val_loss / len(val_loader.dataset)
    f1 = f1_score(all_labels, all_preds, average='macro')
    acc = accuracy_score(all_labels, all_preds)
    return avg_loss, acc, f1

def run_phase(config, model, phase_name, device, writer, start_epoch, global_best_f1, early_stopping):
    print(f"\n{'='*50}\nDÉMARRAGE : {config[phase_name]['description']}\n{'='*50}")
    
    # --- DÉFINITION DES CHEMINS DE SAUVEGARDE ---
    local_save_dir = config['checkpointing']['save_dir']
    drive_save_dir = "/content/drive/MyDrive/AgroScanAI/checkpoints"
    
    # Vérification de sécurité : Le Drive est-il monté ?
    if not os.path.exists("/content/drive/MyDrive"):
        print("⚠️ ALERTE : Google Drive ne semble pas monté ! Les sauvegardes seront perdues.")
        
    os.makedirs(local_save_dir, exist_ok=True)
    os.makedirs(drive_save_dir, exist_ok=True)
    
    train_loader, val_loader = get_dataloaders(config, phase=phase_name)
    
    freeze = config[phase_name]['freeze_backbone']
    if isinstance(freeze, bool) and freeze:
        for param in model.features.parameters(): param.requires_grad = False
    else:
        for param in model.parameters(): param.requires_grad = True
            
    optimizer = optim.AdamW(filter(lambda p: p.requires_grad, model.parameters()), 
                            lr=config[phase_name]['learning_rate'], 
                            weight_decay=config['training']['optimizer']['weight_decay'])
    
    criterion = nn.CrossEntropyLoss(label_smoothing=config['training']['loss']['label_smoothing'])
    scaler = torch.amp.GradScaler(enabled=config['training']['mixed_precision'])
    
    epochs = config[phase_name]['epochs']
    
    scheduler = optim.lr_scheduler.OneCycleLR(
        optimizer, max_lr=config[phase_name]['learning_rate'], 
        steps_per_epoch=len(train_loader), epochs=epochs,
        pct_start=0.3, anneal_strategy='cos'
    )

    prog_resize = config['advanced']['progressive_resizing']

    for epoch in range(epochs):
        current_epoch = start_epoch + epoch
        
        if phase_name == "phase_2" and prog_resize['enabled']:
            for schedule in prog_resize['schedule']:
                if epoch == schedule['epochs']:
                    print(f"\n[Progressive Resizing] Nouvelle taille: {schedule['size']}x{schedule['size']}")
                    train_loader, val_loader = get_dataloaders(config, phase=phase_name, dynamic_img_size=schedule['size'], dynamic_batch_size=schedule['batch_size'])

        model.train()
        train_loss, correct, total = 0.0, 0, 0
        pbar = tqdm(train_loader, desc=f"Epoch {epoch+1}/{epochs} (Global: {current_epoch+1})")
        
        for inputs, labels in pbar:
            inputs, labels = inputs.to(device), labels.to(device)
            optimizer.zero_grad()
            
            with torch.amp.autocast(device_type='cuda', dtype=torch.float16, enabled=config['training']['mixed_precision']):
                outputs = model(inputs)
                loss = criterion(outputs, labels)
            
            scaler.scale(loss).backward()
            if config['advanced']['gradient_clipping'] > 0:
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), config['advanced']['gradient_clipping'])
            scaler.step(optimizer)
            scaler.update()
            
            scheduler.step()
            
            train_loss += loss.item() * inputs.size(0)
            _, predicted = outputs.max(1)
            total += labels.size(0)
            correct += predicted.eq(labels).sum().item()
            pbar.set_postfix({'loss': loss.item(), 'acc': correct/total})
            
        val_loss, val_acc, val_f1 = validate(model, val_loader, criterion, device)
        print(f"Val Loss: {val_loss:.4f} | Val Acc: {val_acc:.4f} | Val F1 (Macro): {val_f1:.4f}")
        
        writer.add_scalar('Loss/Train', train_loss/total, current_epoch)
        writer.add_scalar('Loss/Val', val_loss, current_epoch)
        writer.add_scalar('Metrics/F1_Macro', val_f1, current_epoch)
        
        wandb.log({
            "epoch": current_epoch, "phase": phase_name,
            "train/loss": train_loss/total, "val/loss": val_loss,
            "val/f1_macro": val_f1, "val/acc": val_acc,
            "learning_rate": scheduler.get_last_lr()[0]
        })
        
        # ==========================================
        # === SAUVEGARDE SÉCURISÉE AVEC LOGS ===
        # ==========================================
        best_filename = config['checkpointing'].get('filename', 'best_model.pth')
        
        # 1. Sauvegarde systématique de last_model.pth
        try:
            local_last_path = os.path.join(local_save_dir, 'last_model.pth')
            drive_last_path = os.path.join(drive_save_dir, 'last_model.pth')
            
            torch.save({
                'epoch': current_epoch, 'model_state_dict': model.state_dict(), 'f1': val_f1
            }, local_last_path)
            shutil.copy(local_last_path, drive_last_path)
            print(f"➡️ 'last_model.pth' synchronisé sur le Drive.")
        except Exception as e:
            print(f"❌ Erreur lors de la sauvegarde du last_model sur le Drive : {e}")
        
        # 2. Sauvegarde de best_model.pth UNIQUEMENT si le F1 s'améliore
        if val_f1 > global_best_f1:
            print(f"📈 Amélioration du F1 : {global_best_f1:.4f} ➡️ {val_f1:.4f}")
            global_best_f1 = val_f1
            
            try:
                local_best_path = os.path.join(local_save_dir, best_filename)
                drive_best_path = os.path.join(drive_save_dir, best_filename)
                
                torch.save({
                    'epoch': current_epoch, 'model_state_dict': model.state_dict(), 'f1': val_f1
                }, local_best_path)
                shutil.copy(local_best_path, drive_best_path)
                
                print(f"🌟 NOUVEAU MEILLEUR MODÈLE sauvegardé avec succès sur : {drive_best_path}")
            except Exception as e:
                print(f"❌ Erreur lors de la sauvegarde du best_model sur le Drive : {e}")
        else:
            print(f"📉 F1 actuel ({val_f1:.4f}) n'a pas battu le record ({global_best_f1:.4f}). 'best_model.pth' non mis à jour.")

        early_stopping(val_f1)
        if early_stopping.early_stop:
            print("🛑 Early Stopping déclenché !")
            break
            
    return start_epoch + epochs, global_best_f1

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=str, default="config.yaml")
    parser.add_argument("--resume", type=str, default=None)
    parser.add_argument("--start-phase", type=str, default="phase_1", choices=["phase_1", "phase_2"])
    args = parser.parse_args()

    with open(args.config, 'r') as f: config = yaml.safe_load(f)
    
    wandb.init(project="AgroScanAI", config=config, reinit=True)
    
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    torch.manual_seed(config['project']['seed'])
    
    model = build_model(config).to(device)
    writer = SummaryWriter(log_dir=config['logging']['tensorboard']['log_dir'])
    es = EarlyStopping(patience=config['early_stopping']['patience'], min_delta=config['early_stopping']['min_delta'])
    
    current_epoch = 0
    global_best_f1 = 0.0
    
    if args.resume and os.path.exists(args.resume):
        checkpoint = torch.load(args.resume, map_location=device)
        model.load_state_dict(checkpoint['model_state_dict'])
        current_epoch = checkpoint.get('epoch', 0) + 1
        global_best_f1 = checkpoint.get('f1', 0.0)
        print(f"✅ Modèle chargé. Reprise à l'epoch {current_epoch} (Record F1 à battre : {global_best_f1:.4f})")

    if args.start_phase == "phase_1":
        current_epoch, global_best_f1 = run_phase(config, model, "phase_1", device, writer, current_epoch, global_best_f1, es)
        if not es.early_stop:
            es = EarlyStopping(patience=config['early_stopping']['patience'], min_delta=config['early_stopping']['min_delta'])
            run_phase(config, model, "phase_2", device, writer, current_epoch, global_best_f1, es)

    elif args.start_phase == "phase_2":
        run_phase(config, model, "phase_2", device, writer, current_epoch, global_best_f1, es)
        
    writer.close()
    wandb.finish()
    print("✅ Entraînement terminé !")

if __name__ == "__main__": main()