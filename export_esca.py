
from pathlib import Path
import shutil
import sys

from ultralytics import YOLO

# Windows F: drive, accessed from Ubuntu WSL2.
REPO = Path("/mnt/f/isaaclab/prototype-thesis")

# Existing checkpoint. No retraining occurs.
WEIGHTS = (
    REPO
    / "runs"
    / "quick_driver_state_12000_e35"
    / "yolo11n"
    / "weights"
    / "best.pt"
)

# Final output folder on the Windows F: drive.
DESTINATION = (
    REPO
    / "exports"
    / "android"
    / "quick_driver_state"
)

EXPECTED_NAMES = [
    "eye_closed",
    "eye_open",
    "phone",
    "yawn",
]


def main():
    print("=" * 70, flush=True)
    print("ESCA YOLO11n TO LITERT EXPORT", flush=True)
    print("=" * 70, flush=True)

    print("Python:", sys.executable, flush=True)
    print("Checkpoint:", WEIGHTS, flush=True)

    if not WEIGHTS.is_file():
        raise FileNotFoundError(
            f"Trained model not found: {WEIGHTS}"
        )

    DESTINATION.mkdir(parents=True, exist_ok=True)

    print("\nLoading trained checkpoint...", flush=True)
    model = YOLO(str(WEIGHTS))

    names = model.names
    if isinstance(names, dict):
        labels = [
            names[i]
            for i in range(len(names))
        ]
    else:
        labels = list(names)

    print("Detected class labels:", labels, flush=True)

    if labels != EXPECTED_NAMES:
        raise ValueError(
            "Unexpected class names or order. "
            f"Expected {EXPECTED_NAMES}, got {labels}"
        )

    print("\nStarting LiteRT export...", flush=True)
    print("Input image size: 320 x 320", flush=True)
    print("Export precision: FP32", flush=True)
    print(
        "The first export may install additional "
        "Python dependencies.",
        flush=True,
    )

    exported = Path(
        model.export(
            format="litert",
            imgsz=320,
            batch=1,
            device="cpu",
        )
    )

    print("\nExporter returned:", exported, flush=True)

    # Different Ultralytics versions may return
    # either a .tflite file or an export directory.
    if exported.is_file():
        candidates = (
            [exported]
            if exported.suffix.lower() == ".tflite"
            else []
        )
    elif exported.is_dir():
        candidates = sorted(
            exported.rglob("*.tflite")
        )
    else:
        raise FileNotFoundError(
            f"Export result does not exist: {exported}"
        )

    if len(candidates) != 1:
        raise RuntimeError(
            "Expected exactly one TFLite file, "
            f"found {len(candidates)}: {candidates}. "
            "Inspect the export output manually."
        )

    source_model = candidates[0]

    android_model = (
        DESTINATION / "yolo11n_320_litert_fp32.tflite"
    )
    android_labels = (
        DESTINATION / "yolo11n_labels.txt"
    )

    shutil.copy2(source_model, android_model)

    android_labels.write_text(
        "\n".join(labels) + "\n",
        encoding="utf-8",
    )

    print("\n" + "=" * 70, flush=True)
    print("EXPORT FILES CREATED", flush=True)
    print("=" * 70, flush=True)

    print("Model:", android_model, flush=True)
    print("Labels:", android_labels, flush=True)

    print(
        "Model size:",
        round(android_model.stat().st_size / 1048576, 2),
        "MiB",
        flush=True,
    )

    print("\nLabels:", flush=True)
    for index, label in enumerate(labels):
        print(f"  {index}: {label}", flush=True)

    print("\nEXPORT COMPLETED", flush=True)
    print(
        "Android runtime compatibility has not "
        "yet been verified.",
        flush=True,
    )


if __name__ == "__main__":
    main()
