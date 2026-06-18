import os
import torch
import torch.nn as nn
import subprocess
import tensorflow as tf
import numpy as np
import yaml
import random
from PIL import Image
from model import build_model

class DualOutputWrapper(nn.Module):
    def __init__(self, base_model):
        super().__init__()
        self.base_model = base_model

    def forward(self, x):
        features = self.base_model.features(x)
        x_pool = self.base_model.avgpool(features)
        x_flat = torch.flatten(x_pool, 1)
        logits = self.base_model.classifier(x_flat)
        return features, logits

def main():
    with open("config.yaml", 'r') as f: config = yaml.safe_load(f)
        
    checkpoint_path = os.path.join(config['checkpointing']['save_dir'], config['checkpointing']['filename'])
    base_model = build_model(config)
    
    if not os.path.exists(checkpoint_path):
        raise FileNotFoundError(f"Checkpoint {checkpoint_path} introuvable. Entraînez le modèle d'abord.")
        
    checkpoint = torch.load(checkpoint_path, map_location=torch.device('cpu'))
    base_model.load_state_dict(checkpoint['model_state_dict'])
    
    dual_model = DualOutputWrapper(base_model)
    dual_model.eval()
    
    export_dir = "./export"
    os.makedirs(export_dir, exist_ok=True)
    onnx_path = os.path.join(export_dir, "model_dual.onnx")
    tf_dir = os.path.join(export_dir, "saved_model_dual")
    
    img_size = config['image']['input_size'][0]
    dummy_input = torch.randn(1, 3, img_size, img_size)
    
    print("\n[1/4] Export PyTorch -> ONNX (Double Sortie)")
    torch.onnx.export(dual_model, dummy_input, onnx_path, export_params=True, opset_version=13, 
                      input_names=['input'], output_names=['feature_maps', 'logits'], 
                      dynamic_axes={'input': {0: 'batch_size'}})
                      
    print("\n[2/4] Export ONNX -> TensorFlow SavedModel")
    subprocess.run(["onnx2tf", "-i", onnx_path, "-o", tf_dir, "-rtf"], check=True)
    
    print("\n[3/4] Génération TFLite FP32")
    converter_fp32 = tf.lite.TFLiteConverter.from_saved_model(tf_dir)
    with open(os.path.join(export_dir, "AgroScan_FP32.tflite"), 'wb') as f: f.write(converter_fp32.convert())
    
    print("\n[4/4] Génération TFLite INT8 (Calibration)")
    pv_clean_path = config['dataset']['sources']['plantvillage']['path']
    mean, std = np.array(config['image']['mean'], dtype=np.float32), np.array(config['image']['std'], dtype=np.float32)
    
    all_paths = [os.path.join(r, f) for r, _, fs in os.walk(pv_clean_path) for f in fs if f.lower().endswith(('.jpg','.png'))]
    random.seed(config['project']['seed'])
    selected = random.sample(all_paths, min(100, len(all_paths)))

    def rep_data():
        for p in selected:
            img = Image.open(p).convert('RGB').resize((img_size, img_size), Image.Resampling.BILINEAR)
            img_data = (np.array(img, dtype=np.float32)/255.0 - mean) / std
            yield [np.expand_dims(img_data, axis=0).astype(np.float32)]

    converter_int8 = tf.lite.TFLiteConverter.from_saved_model(tf_dir)
    converter_int8.optimizations = [tf.lite.Optimize.DEFAULT]
    converter_int8.representative_dataset = rep_data
    converter_int8.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter_int8.inference_input_type = tf.int8
    converter_int8.inference_output_type = tf.float32
    
    with open(os.path.join(export_dir, "AgroScan_INT8.tflite"), 'wb') as f: f.write(converter_int8.convert())
    
    with open(os.path.join(export_dir, "temperature.txt"), 'w') as f: f.write("1.0")
    print(f"\n✅ Terminé ! Fichiers disponibles dans {export_dir}/")

if __name__ == "__main__": main()