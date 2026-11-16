from pathlib import Path
import argparse
import hashlib
import json
import platform
import shutil
import sys
from ultralytics import YOLO

REPO = Path(__file__).resolve().parent
DEFAULT_WEIGHTS = 'runs/quick_driver_state_12000_e35/yolo11n/weights/best.pt'
DEFAULT_OUT = 'exports/android/quick_driver_state/yolo11n_320_litert_fp32.tflite'
EXPECTED = ['eye_closed', 'eye_open', 'phone', 'yawn']

def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def main():
    ap = argparse.ArgumentParser(description='Export an existing ESCA YOLO checkpoint to LiteRT FP32 on Linux.')
    ap.add_argument('--weights', default=DEFAULT_WEIGHTS)
    ap.add_argument('--out', default=DEFAULT_OUT)
    args = ap.parse_args()
    if sys.platform != 'linux' or platform.machine().lower() not in ('x86_64', 'amd64'):
        raise SystemExit('Run this script using Ubuntu WSL on an x86-64 computer.')
    weights = Path(args.weights)
    weights = weights if weights.is_absolute() else REPO / weights
    out = Path(args.out)
    out = out if out.is_absolute() else REPO / out
    if not weights.is_file():
        raise FileNotFoundError(f'Missing checkpoint: {weights}')
    model = YOLO(str(weights))
    names = [model.names[i] for i in sorted(model.names)] if isinstance(model.names, dict) else list(model.names)
    if names != EXPECTED:
        raise ValueError(f'Class names/order mismatch: {names}')
    print('Using trained checkpoint:', weights, flush=True)
    print('Starting Linux LiteRT FP32 export. This does not retrain YOLO.', flush=True)
    exported = Path(model.export(format='litert', imgsz=320, batch=1, device='cpu'))
    if exported.is_file() and exported.suffix.lower() == '.tflite':
        candidates = [exported]
    elif exported.is_dir():
        candidates = sorted(exported.rglob('*.tflite'))
    else:
        candidates = []
    fp32 = [f for f in candidates if 'float32' in f.name.lower() or 'fp32' in f.name.lower()]
    if len(candidates) == 1:
        source = candidates[0]
    elif len(fp32) == 1:
        source = fp32[0]
    else:
        raise RuntimeError(f'Cannot identify a single FP32 TFLite model: {candidates}')
    if not source.is_file() or not source.stat().st_size:
        raise RuntimeError(f'Export did not produce a nonempty TFLite file: {source}')
    out.parent.mkdir(parents=True, exist_ok=True)
    if source.resolve() != out.resolve():
        shutil.copy2(source, out)
    sidecar = Path(str(out) + '.source.json')
    sidecar.write_text(json.dumps({
        'weights': str(weights), 'weights_sha256': digest(weights),
        'tflite': str(out), 'tflite_sha256': digest(out),
        'class_names': names, 'image_size': 320,
        'export_format': 'litert', 'requested_precision': 'FP32',
        'runtime_verified': False
    }, indent=2) + '\n', encoding='utf-8')
    print('Export complete:', out)
    print('Source-check file:', sidecar)
    print('Android runtime compatibility still requires testing.')

if __name__ == '__main__':
    main()
