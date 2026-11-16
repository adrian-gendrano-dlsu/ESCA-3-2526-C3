## **Step 1. Download the small conversion utility**

Place prepare_dmd_study.py directly inside your repository folder.

```
F:\isaaclab\prototype-thesis\
├── prepare_dmd_study.py       ← NEW FILE
├── dms\
├── scripts\
├── models\
├── configs\
└── ...
```

The utility reads existing annotations. It does not automatically label videos or train anything.

I tested its conversion logic with a representative OpenLABEL annotation and a generated test video. I have **not** tested it against a downloaded DMD video, so its first inspection step is necessary.

## **Step 2. Download an existing annotated video**

1. Visit [the official DMD website](https://dmd.vicomtech.org/?utm_source=chatgpt.com).
2. Locate the dataset download section and choose **Drowsiness**.
3. Follow the website's academic-access instructions.
4. Download a small selection of permitted **RGB** recordings and their corresponding temporal annotation JSON files.
5. Start with one drowsiness recording, identified as an `s5` session in the DMD documentation.

The official dataset describes eye-state and yawning annotations, including yawning with and without the hand covering the mouth.

You need two matching files for each test session.

- A prerecorded `.mp4` video with a visible driver.
- Its corresponding temporal `.json` annotation.

**Use the synchronized video corresponding to the annotations.** DMD originally used several cameras and annotated synchronized recordings. A face-camera video with a different starting timestamp can produce incorrect F1 scores even if both files are genuine.

You may need the dataset's alignment tools if the individual face video is not synchronized. Do not assume matching subject IDs alone establish synchronization.

You can put the downloaded files in a folder such as

```
F:\isaaclab\public_dmd\
├── your_video.mp4
└── your_annotations.json
```

Those two names are examples. Keep the real downloaded filenames and use their actual paths in the next steps.

## **Step 3. Open the correct Python environment**

In VS Code, open your `prototype-thesis` repository and open a **PowerShell terminal**.

Enter

```
cd "F:\isaaclab\prototype-thesis"

$py = "F:\isaaclab\env_isaaclab\Scripts\python.exe"

& $py --version
```

Then confirm that the required files exist.

```
Test-Path ".\prepare_dmd_study.py"
Test-Path ".\scripts\run_monitor.py"
Test-Path ".\scripts\evaluate_study.py"
Test-Path ".\models\face_landmarker.task"
Test-Path ".\models\esca_fusion_phase2.json"
Test-Path ".\runs\quick_driver_state_12000_e35\yolo11n\weights\best.pt"
```

Each command should return `True`. If any return `False`, correct the missing file or path before proceeding.

## **Step 4. Tell PowerShell which downloaded files to use**

Copy this block into the same terminal.

```
$video = "F:\isaaclab\public_dmd\your_video.mp4"
$annotation = "F:\isaaclab\public_dmd\your_annotations.json"

$participant = "DMD01"
$session = "DMD01_s5"
```

Change **only the first two values** to the actual file paths you downloaded.

`DMD01` and `DMD01_s5` are local identifiers for your experiment. They do not need to be the original filenames, but the participant identifier must correspond consistently to the actual DMD subject.

Check the paths.

```
Test-Path $video
Test-Path $annotation
```

Both must return `True`.

## **Step 5. Inspect the annotations before converting them**

Run

```
& $py .\prepare_dmd_study.py `
  --video $video `
  --annotation $annotation `
  --participant $participant `
  --session $session `
  --split validation `
  --out data/public_dmd `
  --inspect-only
```

This prints an inventory of the annotation labels, the video's frame rate, and the video duration.

Look for annotation labels similar to these.

```
'Yawning without hand' | 'Yawning without hand' | yawning
'Yawning with hand'    | 'Yawning with hand'    | yawning
'Closed'               | 'Closed'               | eye_closure
'Opened'               | 'Opened'               | IGNORED
```

The actual output depends on the downloaded JSON file.

For this initial experiment, confirm that `Converted yawning` reports at least one event. If it reports zero, stop and check whether you selected the correct DMD annotation. The utility deliberately refuses to invent missing positive labels.

It interprets `Closed` as an optional prolonged eye-closure event when the annotated interval lasts at least 0.5 seconds. It does not claim that eye closure alone establishes the driver's overall drowsiness state.

## **Step 6. Create the evaluation files**

After the inspection succeeds, run the same command **without** `--inspect-only`.

```
& $py .\prepare_dmd_study.py `
  --video $video `
  --annotation $annotation `
  --participant $participant `
  --session $session `
  --split validation `
  --out data/public_dmd
```

This creates the following.

```
data\public_dmd\
├── manifest.csv
├── no_calibration.yaml
└── labels\
    └── DMD01_s5.csv
```

The generated label CSV will have this structure.

```
behavior,start_s,end_s
yawning,10.2,13.5
eye_closure,25.1,26.3
```

These numbers are illustrative. Your actual values come from the DMD annotations.

The `no_calibration.yaml` file disables the initial personalized calibration for this public-video experiment. This avoids automatically using a potentially nonneutral opening segment as the driver's baseline. It also means these results represent a specific **uncalibrated test configuration**, rather than the full personalized calibration procedure described in the proposal.

## **Step 7. Run your existing model on the prerecorded video**

This is the step that generates the `frames.jsonl` data required by `evaluate_study.py`.

Copy and run

```
& $py .\scripts\run_monitor.py `
  --source $video `
  --yolo "runs/quick_driver_state_12000_e35/yolo11n/weights/best.pt" `
  --fusion "models/esca_fusion_phase2.json" `
  --config "data/public_dmd/no_calibration.yaml" `
  --imgsz 320 `
  --no-show `
  --quiet `
  --log-dir "data/public_dmd/sessions" `
  --session-name $session
```

This command does several things.

1. Opens the existing video instead of a camera.
2. Uses your trained YOLO11n checkpoint.
3. Loads your trained fusion model.
4. Processes the frames through the actual ESCA monitoring pipeline.
5. Records the features and behavior decisions.
6. Saves the results for subsequent evaluation.

It does not use ESP32 hardware.

After it finishes, check that these files exist.

```
Test-Path "data/public_dmd/sessions/$session/frames.jsonl"
Test-Path "data/public_dmd/sessions/$session/summary.json"
```

Both should return `True`.

If the video cannot be decoded or no face is detected, investigate that before trusting any scores. In particular, inspect whether the video is showing the driver's face at a usable size.

## **Step 8. Evaluate the original rules**

Now run `evaluate_study.py` using your current recorded features, but with the original rules selected.

```
& $py .\scripts\evaluate_study.py `
  --manifest "data/public_dmd/manifest.csv" `
  --sessions "data/public_dmd/sessions" `
  --split validation `
  --config "data/public_dmd/no_calibration.yaml" `
  --eye-mode rules `
  --mouth-mode rules `
  --tag dmd_rules
```

The output is saved under

```
results\study_eval\dmd_rules\
├── per_session.csv
├── summary.csv
├── summary.md
└── report.json
```

Open `summary.md` in VS Code.

## **Step 9. Evaluate the trained fusion model**

Next, use the same recordings, annotations, and configuration, changing only the evidence modes.

```
& $py .\scripts\evaluate_study.py `
  --manifest "data/public_dmd/manifest.csv" `
  --sessions "data/public_dmd/sessions" `
  --split validation `
  --config "data/public_dmd/no_calibration.yaml" `
  --eye-mode fused `
  --mouth-mode fused `
  --tag dmd_fused
```

Open

```
results\study_eval\dmd_fused\summary.md
```

This comparison is fairer because both versions use the same source video and logged measurements.

Important distinction

The `--eye-mode` and `--mouth-mode` options replay ESCA's behavior-decision stages. They do not rerun YOLO or MediaPipe. Consequently, this particular comparison measures the contribution of the trained fusion evidence, rather than differences between two YOLO checkpoints. The repository explicitly describes replay as re-running Stages 3–5 on stored features. [View evaluate_study.py source](https://github.com/idkshan/prototype-thesis/blob/main/scripts/evaluate_study.py?utm_source=chatgpt.com)

## **Step 10. Interpret the results correctly**

Look for these columns in `summary.csv`.

| Column            | Meaning                                          |
| ----------------- | ------------------------------------------------ |
| `behavior`        | Behavior being evaluated                         |
| `labelled_events` | Number of ground-truth events                    |
| `primary_view`    | Whether the primary metric uses frames or events |
| `primary_f1`      | F1 score for that primary metric                 |
| `target_f1`       | Proposal threshold                               |
| `event_precision` | How many detected events were correct            |
| `event_recall`    | How many actual events were detected             |
| `event_f1`        | Event-based F1                                   |
| `frame_f1`        | Frame-based F1                                   |

For this initial DMD drowsiness experiment, interpret **yawning** and, when present, **eye_closure**.

Other behaviors might appear in the report even though the converted annotation CSV does not contain their ground-truth labels. **Do not report their F1 values as measured performance.** The evaluator treats time outside a labeled event as negative, so missing annotations can lead to misleading false positives and zero scores.

For example, a comparison table you could construct would look like this.

| Behavior                        | Rules F1         | Fusion F1        | Proposal target                        |
| ------------------------------- | ---------------- | ---------------- | -------------------------------------- |
| Yawning, event-level            | From `dmd_rules` | From `dmd_fused` | 0.80                                   |
| Eye closure, event-level        | From `dmd_rules` | From `dmd_fused` | No separate thesis target in the guide |
| Overall drowsiness, frame-level | Not assessed     | Not assessed     | 0.85                                   |
| Gaze deviation                  | Not assessed     | Not assessed     | 0.85                                   |
| Phone usage                     | Not assessed     | Not assessed     | 0.80                                   |

The guide and evaluation implementation distinguish frame-based drowsiness scoring from event-based yawning, gaze, and phone scoring.



## **Step 11. Extend the experiment to more people**

Once the first video works, you can add other independently annotated DMD sessions using the same conversion utility.

For each new subject, assign a new participant identifier, such as `DMD02`, and a unique session name.

Use `--split validation` for sessions used to inspect and tune the system. Reserve other subjects for `--split test`.

Keep every session belonging to the same person in only one split.

After deciding the settings using validation, evaluate the reserved test subjects with the frozen configuration. That follows the workflow in Part 9 of `Guide.txt`.



One DMD video can establish whether the pipeline runs and provide a preliminary result, but it cannot establish generalization across drivers.

## **Troubleshooting**

| Problem                                    | What to check                                                                                                      |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| `No OpenLABEL/VCD actions found`           | Check that you downloaded the temporal annotation JSON rather than another metadata file                           |
| `No yawning or closed-eye intervals found` | Check that it is a drowsiness annotation and inspect the printed action names                                      |
| Annotations run beyond the video duration  | Check that the video and annotations belong to the same synchronized recording                                     |
| `Missing files` when evaluating            | Confirm that `frames.jsonl`, `manifest.csv`, and the converted label CSV exist                                     |
| `No sessions for split 'test'`             | You currently have only validation sessions. Use `--split validation` until independent test sessions are prepared |
| Almost no faces detected                   | Check video framing, face size, lighting, and camera selection                                                     |
| Unreasonably low yawning F1                | Verify annotation timing, face detection, model weights, and whether DMD's yawning definitions match ESCA's        |

## **What these experiments can establish**

**The external JPG benchmark**

Tests image-level eye and mouth recognition on the Raspberry Pi 5 dataset, using its existing labels.

**The DMD video evaluation**

Tests temporal behavior detection for the behaviors whose annotation definitions and timestamps have been verified.

**The full thesis objectives**

Still require a separate assessment of all four behaviors, alarms, processing performance, and system deployment conditions. Public videos can support some of these tests, but cannot establish ESP32 camera latency, Android processing speed, GPS accuracy, or actual hardware uptime.

One final detail is worth documenting in the thesis. DMD's annotations and ESCA's behavior rules do not necessarily define each event in exactly the same way. For example, DMD's hand-covered yawning annotation can begin when the hand starts moving toward the mouth, which is earlier than the visible mouth-opening interval ESCA is designed to detect. This needs to be disclosed when interpreting event-level F1.

The conversion script preserves the dataset's annotated time intervals rather than silently changing them to improve agreement.

For your present situation, I would run the DMD yawning comparison first, keep the existing Raspberry Pi 5 image benchmark as a separate experiment, and avoid treating either result as proof of all the proposal's targets.