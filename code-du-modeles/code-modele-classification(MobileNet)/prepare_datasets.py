import os
import shutil
import json
import pandas as pd
import hashlib

# --- CHEMINS ---
BASE_PATH = r"C:\Users\mbele\Documents\session ete\atelier-IA\AgroScan AI\code-du-modele"
DATA_PATH = os.path.join(BASE_PATH, "data")

def get_file_hash(filepath):
    hasher = hashlib.md5()
    with open(filepath, 'rb') as f:
        buf = f.read()
        hasher.update(buf)
    return hasher.hexdigest()

def filter_plantvillage():
    print("--- Nettoyage PlantVillage ---")
    source = os.path.join(DATA_PATH, "PlantVillage")
    dest = os.path.join(DATA_PATH, "PlantVillage_Clean")
    
    seen_hashes = set()
    duplicates = 0
    processed = 0
    
    for cls in os.listdir(source):
        src_cls_path = os.path.join(source, cls)
        if os.path.isdir(src_cls_path):
            dst_cls_path = os.path.join(dest, cls)
            os.makedirs(dst_cls_path, exist_ok=True)
            for filename in os.listdir(src_cls_path):
                file_path = os.path.join(src_cls_path, filename)
                if os.path.isfile(file_path):
                    file_hash = get_file_hash(file_path)
                    if file_hash not in seen_hashes:
                        shutil.copy2(file_path, os.path.join(dst_cls_path, filename))
                        seen_hashes.add(file_hash)
                        processed += 1
                    else:
                        duplicates += 1
    print(f"PlantVillage : {processed} images copiées, {duplicates} doublons supprimés.")

def filter_plantdoc():
    print("--- Filtrage PlantDoc ---")
    source_base = os.path.join(DATA_PATH, "PlantDoc")
    source_dirs = [os.path.join(source_base, "train"), os.path.join(source_base, "test")]
    dest = os.path.join(DATA_PATH, "PlantDoc_Filtered_38")
    mapping_file = os.path.join(DATA_PATH, "plantdoc_class_mapping.json")
    
    seen_hashes = set()
    duplicates = 0
    processed = 0
    with open(mapping_file, 'r') as f:
        mapping = json.load(f)
        
    for source in source_dirs:
        if not os.path.exists(source): continue
        for subfolder in os.listdir(source):
            if subfolder in mapping:
                target_class = mapping[subfolder]
                dst_path = os.path.join(dest, target_class)
                os.makedirs(dst_path, exist_ok=True)
                src_path = os.path.join(source, subfolder)
                for filename in os.listdir(src_path):
                    file_path = os.path.join(src_path, filename)
                    if os.path.isfile(file_path):
                        file_hash = get_file_hash(file_path)
                        if file_hash not in seen_hashes:
                            shutil.copy2(file_path, os.path.join(dst_path, filename))
                            seen_hashes.add(file_hash)
                            processed += 1
                        else:
                            duplicates += 1
    print(f"PlantDoc : {processed} images copiées, {duplicates} doublons supprimés.")

def filter_pathology():
    print("--- Filtrage PlantPathology ---")
    base_pp = os.path.join(DATA_PATH, "plant-pathology-2021-fgvc8")
    csv_path = os.path.join(base_pp, "train.csv")
    img_dirs = [os.path.join(base_pp, "train_images"), os.path.join(base_pp, "test_images")]
    dest = os.path.join(DATA_PATH, "PlantPathology2021_Filtered_38")
    
    label_map = {"scab": "Apple___Apple_scab", "rust": "Apple___Cedar_apple_rust", "healthy": "Apple___healthy"}
    df = pd.read_csv(csv_path)
    seen_hashes = set()
    duplicates = 0
    processed = 0
    
    for _, row in df.iterrows():
        label = row['labels']
        img_name = row['image']
        if label in label_map:
            target_cls = label_map[label]
            dst_path = os.path.join(dest, target_cls)
            os.makedirs(dst_path, exist_ok=True)
            src = next((os.path.join(d, img_name) for d in img_dirs if os.path.exists(os.path.join(d, img_name))), None)
            if src:
                file_hash = get_file_hash(src)
                if file_hash not in seen_hashes:
                    shutil.copy2(src, os.path.join(dst_path, img_name))
                    seen_hashes.add(file_hash)
                    processed += 1
                else:
                    duplicates += 1
    print(f"PlantPathology : {processed} images copiées, {duplicates} doublons supprimés.")

if __name__ == "__main__":
    filter_plantvillage()
    filter_plantdoc()
    filter_pathology()
    print("--- Opération terminée avec succès. ---")