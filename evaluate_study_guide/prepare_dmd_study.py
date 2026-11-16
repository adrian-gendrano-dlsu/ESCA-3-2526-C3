#!/usr/bin/env python3
"""Convert one *matching* DMD OpenLABEL/VCD video annotation to ESCA study labels.

This is an adapter, NOT an annotation generator. It only converts DMD's
pre-existing temporal annotations. It does NOT infer drowsiness from eye closure,
OR gaze/phone from class names. Check video/annotation synchronization yourself.

Example (run from the ESCA repository's root):
    python prepare_dmd_study.py --video "F:\\DMD\\my_video.mp4" \
        --annotation "F:\\DMD\\my_video_ann.json" --participant DMD01 \
        --split validation --out data/public_dmd
"""
import argparse
import csv
import json
import re
from pathlib import Path

import cv2


def norm(value):
    return re.sub(r'_+', '_', re.sub(r'[^a-z0-9]+', '_', str(value).lower())).strip('_')


def classify(name, typ):
    """Map exact DMD temporal action labels to supported ESCA behaviors.

    In DMD's OpenLABEL, `name` is often empty and `type` is hierarchical:
        eyes_state/close
        yawning/Yawning with hand
        yawning/Yawning without hand
    Never map `eyes_state/closing` or `blinks/blinking` to sustained closure.
    """
    path = [norm(part) for part in str(typ).split('/') if part.strip()]
    if len(path) >= 2:
        category, action = path[-2], path[-1]
        if category == 'yawning' and action in {'yawning_with_hand', 'yawning_without_hand',
                                                'yawn_with_hand', 'yawn_without_hand'}:
            return 'yawning'
        if category in {'eyes_state', 'eye_state'} and action in {'close', 'closed'}:
            return 'eye_closure'
        # Other hierarchical types are explicitly left unlabelled, even when
        # their names contain words like "closing" or "blinking".
        return None
    # Also accept older, unqualified annotation names, if present.
    simple = {norm(name), norm(typ)}
    if simple.intersection({'yawn', 'yawning', 'yawning_with_hand',
                            'yawning_without_hand', 'yawn_with_hand', 'yawn_without_hand'}):
        return 'yawning'
    if simple.intersection({'closed', 'close', 'eyes_closed', 'eye_closed'}):
        return 'eye_closure'
    return None


def parse_annotations(annotation):
    raw = json.loads(annotation.read_text(encoding='utf-8-sig'))
    doc = raw.get('openlabel', raw.get('vcd', raw))
    actions = doc.get('actions', {})
    if not isinstance(actions, dict) or not actions:
        raise ValueError('No OpenLABEL/VCD actions found. Check that this JSON is the DMD TEMPORAL annotation for the corresponding recording.')
    events, seen = [], []
    for key, a in actions.items():
        if not isinstance(a, dict):
            continue
        name, typ = str(a.get('name', '')), str(a.get('type', ''))
        role = classify(name, typ)
        intervals = a.get('frame_intervals') or []
        seen.append((key, name, typ, role or 'IGNORED', len(intervals)))
        for interval in intervals:
            try:
                start, end = int(interval['frame_start']), int(interval['frame_end'])
            except (KeyError, ValueError, TypeError) as ex:
                raise ValueError(f'Invalid frame interval for action {key}: {interval!r}') from ex
            if start < 0 or end < start:
                raise ValueError(f'Invalid frame interval for action {key}: {start}-{end}')
            if role:
                events.append((role, start, end))
    return seen, events


def merge_intervals(items):
    out = []
    for s, e in sorted(items):
        if out and s <= out[-1][1] + 1:
            out[-1] = (out[-1][0], max(out[-1][1], e))
        else:
            out.append((s, e))
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--video', required=True, help='MATCHING, synchronized DMD RGB video, in .mp4 format')
    p.add_argument('--annotation', required=True, help='matching temporal DMD annotation, OpenLABEL/VCD .json')
    p.add_argument('--participant', required=True, help='DMD subject code, e.g. DMD01, never filename guess')
    p.add_argument('--split', choices=['validation', 'test'], default='validation')
    p.add_argument('--session', help='session name, default is the filename without extension')
    p.add_argument('--out', default='data/public_dmd')
    p.add_argument('--inspect-only', action='store_true', help='read names and intervals; do not write any files')
    args = p.parse_args()

    video, anno, root = Path(args.video).expanduser().resolve(), Path(args.annotation).expanduser().resolve(), Path(args.out)
    if not video.is_file() or not anno.is_file():
        raise SystemExit(f'Input missing. Video exists={video.is_file()}: {video}; JSON exists={anno.is_file()}: {anno}')
    if video.suffix.lower() not in {'.mp4', '.avi', '.mkv', '.mov'}:
        raise SystemExit('Use an actual video file, preferably the matching synchronized DMD RGB MP4.')
    session = args.session or video.stem
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', session):
        raise SystemExit('Session name contains spaces or special characters. Supply --session DMD01_s5')
    participant = args.participant.strip()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', participant):
        raise SystemExit('Participant code must contain only letters, digits, underscores, periods or hyphens.')

    cap = cv2.VideoCapture(str(video))
    if not cap.isOpened():
        raise SystemExit(f'Could not open video: {video}')
    fps = float(cap.get(cv2.CAP_PROP_FPS))
    nframes = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.release()
    if not (1 <= fps <= 240 and nframes >= 2):
        raise SystemExit(f'Unusable or uncertain video FPS/frames: {fps} / {nframes}')
    print(f'Video: {video}\nFrames: {nframes} | FPS: {fps:.5f} | Duration: {nframes/fps:.1f} seconds')
    seen, found = parse_annotations(anno)
    print(f'Annotation contains {len(seen)} action definitions, {len(found)} supported intervals before merging.')
    print('Action name | type | ESCA mapping | intervals')
    for key, name, typ, role, n in seen:
        print(f'  {name!r} | {typ!r} | {role} | {n}')
    print('Only yawning and optional sustained eye_closure are mapped. Drowsiness, phone and gaze remain UNLABELLED.')

    if not found:
        raise SystemExit('No yawning or closed-eye intervals found. Check you selected a DMD drowsiness (s5) annotation. No files written.')
    over = [(r,s,e) for r,s,e in found if e >= nframes+round(fps)]
    if over:
        raise SystemExit('Annotations run past video duration by >1 second. Likely a WRONG or UNSYNCHRONIZED video/annotation pair. Example: ' + str(over[0]))

    rows=[]
    for role in ('yawning','eye_closure'):
        xs=merge_intervals([(s,e) for r,s,e in found if r==role])
        for s,e in xs:
            start, stop = s/fps, min(nframes/fps, (e+1)/fps)
            if role == 'eye_closure' and stop-start < 0.50:
                continue  # short eye closures may be blinks, not Stage-3 events
            rows.append((role, round(start, 6), round(stop, 6)))
    rows.sort(key=lambda x:(x[1],x[0]))
    for role in ('yawning','eye_closure'):
        subset=[x for x in rows if x[0]==role]
        print(f'Converted {role}: {len(subset)} events; total {sum(e-s for _,s,e in subset):.1f} sec')
    if not any(r == 'yawning' for r,_,_ in rows):
        raise SystemExit('No positive yawning events were converted. Refusing to create a misleading yawning test.')
    if args.inspect_only:
        print('INSPECT ONLY. No files written.')
        return

    labels_dir = root/'labels'
    labels_dir.mkdir(parents=True, exist_ok=True)
    label_name = f'{session}.csv'
    labels_path = labels_dir/label_name
    if labels_path.exists():
        raise SystemExit(f'{labels_path} exists. Rename the session or remove this output only after checking it.')
    with labels_path.open('w',newline='',encoding='utf-8') as f:
        w=csv.writer(f); w.writerow(['behavior','start_s','end_s']);w.writerows(rows)

    manifest = root/'manifest.csv'
    previous=[]
    if manifest.exists():
        with manifest.open(newline='',encoding='utf-8-sig') as f:
            previous=list(csv.DictReader(f))
    if any(r['session']==session for r in previous):
        labels_path.unlink()
        raise SystemExit(f'Manifest already has {session}. No changes made.')
    if any(r['participant']==participant and r['split'] != args.split for r in previous):
        labels_path.unlink()
        raise SystemExit('Same participant in validation AND test. No changes made.')
    previous.append({'participant':participant,'session':session,
                     'labels':f'labels/{label_name}','split':args.split,'condition':'public_dmd'})
    with manifest.open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=['participant','session','labels','split','condition'])
        w.writeheader();w.writerows(previous)
    config_path = root/'no_calibration.yaml'
    if not config_path.exists():
        config_path.write_text('calibration:\n  enabled: false\n', encoding='utf-8')
    print(f'Wrote ground-truth intervals: {labels_path}\nWrote manifest: {manifest}\nSettings: {config_path}')
    print('Run run_monitor.py on THIS SAME VIDEO using --log-dir '+str(root/'sessions')+' --session-name '+session)
    print('IMPORTANT: Check that annotation time intervals visually align with the video BEFORE using F1.')

if __name__=='__main__':
    main()
