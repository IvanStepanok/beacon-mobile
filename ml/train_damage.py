"""
Beacon damage classifier — COMBINED training (wildfire DINS + PEER Hub ImageNet Task 7).

Why: the wildfire-only model is strong on minimal/complete but near-useless on the middle
'partial' tier (F1 0.226) because wildfire damage is bimodal — 'partial' is rare/ambiguous.
PEER Φ-Net Task 7 (Damage Level: Undamaged/Minor/Moderate/Heavy) is ground-level
post-earthquake structural imagery rich in exactly that middle, so we fold it in to fix partial.

Datasets:
  - Wildfire: HF kevincluo/structure_wildfire_damage_classification  (CC-BY-4.0)
  - Φ-Net Task 7: local .npy (X 224x224x3 caffe-BGR-mean-subtracted, y one-hot 4-class)
      LICENSE: CC-BY-NC-SA-4.0  → the resulting MODEL is non-commercial (humanitarian use);
      Beacon CODE stays Apache-2.0. Request the data from PEER (apps.peer.berkeley.edu/phi-net).
      Point PHINET_TASK7_DIR at the download (default: ~/Downloads/task7).

Tier mapping (both sources → Beacon's mandated 3 tiers minimal/partial/complete):
  wildfire: no_damage/affected→minimal · minor/major→partial · destroyed→complete
  Φ-Net:    Undamaged→minimal · Minor+Moderate→partial · Heavy→complete

Balance/speed (CPU-only): keep ALL Φ-Net + ALL wildfire 'partial'; subsample the
over-represented easy wildfire classes (cap each) so the easy tiers stay strong without
dominating or slowing the run.

Outputs to OUT (default ml/out_combined/) — does NOT overwrite the live ml/out/ until verified:
  damage_classifier.tflite · DamageClassifier.mlpackage · labels.txt · metadata.json · confusion_matrix.txt
Env: SMOKE=1 runs a tiny fast validation (few hundred imgs, 1 epoch). CAP_EASY caps each easy
wildfire class (default 3000).
"""
import collections
import json
import os
import random

import numpy as np
import tensorflow as tf
from datasets import load_dataset

IMG = 224
BATCH = 32
SEED = 1234
SMOKE = os.environ.get("SMOKE") == "1"
CAP_EASY = int(os.environ.get("CAP_EASY", "3000"))
OUT = os.environ.get("OUT", os.path.join(os.path.dirname(__file__), "out_combined"))
PHINET_DIR = os.environ.get("PHINET_TASK7_DIR", os.path.expanduser("~/Downloads/task7"))
os.makedirs(OUT, exist_ok=True)
random.seed(SEED)
np.random.seed(SEED)
tf.random.set_seed(SEED)

TIERS = ["minimal", "partial", "complete"]          # 0,1,2 == DamageTier order on mobile
CAFFE_BGR_MEAN = np.array([103.939, 116.779, 123.68], dtype=np.float32)


def wf_tier_of(name: str):
    n = name.strip().lower().replace("-", "_").replace(" ", "_")
    if "inaccessible" in n:
        return None
    if "destroyed" in n:
        return 2
    if "minor" in n or "major" in n:
        return 1
    if "no_damage" in n or "affected" in n:
        return 0
    return None


# Φ-Net Task 7 label registration: 0 Heavy, 1 Minor, 2 Moderate, 3 Undamaged
PHI_TIER = {0: 2, 1: 1, 2: 1, 3: 0}


def log(*a):
    print(*a, flush=True)


def load_phinet():
    """Return (X_train_idx_fn, y_tier_train, X_test_idx_fn, y_tier_test) with raw-RGB recovery."""
    xtr = np.load(os.path.join(PHINET_DIR, "task7_X_train.npy"), mmap_mode="r")
    ytr = np.load(os.path.join(PHINET_DIR, "task7_y_train.npy")).argmax(1)
    xte = np.load(os.path.join(PHINET_DIR, "task7_X_test.npy"), mmap_mode="r")
    yte = np.load(os.path.join(PHINET_DIR, "task7_y_test.npy")).argmax(1)

    def recover(x):  # caffe BGR mean-subtracted -> raw RGB [0,255]
        r = np.asarray(x, dtype=np.float32) + CAFFE_BGR_MEAN
        r = r[..., ::-1]
        return np.clip(r, 0.0, 255.0)

    return (xtr, np.array([PHI_TIER[int(c)] for c in ytr], np.int32),
            xte, np.array([PHI_TIER[int(c)] for c in yte], np.int32), recover)


def main():
    log(f">> SMOKE={SMOKE} CAP_EASY={CAP_EASY} OUT={OUT} PHINET_DIR={PHINET_DIR}")

    # ---- wildfire (HF) ----
    log(">> loading wildfire (HF) ...")
    ds = load_dataset("kevincluo/structure_wildfire_damage_classification", verification_mode="no_checks")
    first = next(iter(ds.values()))
    img_col = next((c for c, f in first.features.items() if f.__class__.__name__ == "Image"), "image")
    lbl_col = next((c for c, f in first.features.items() if f.__class__.__name__ == "ClassLabel"), "label")
    class_names = first.features[lbl_col].names
    src2tier = {i: wf_tier_of(nm) for i, nm in enumerate(class_names)}
    log(">> wildfire class->tier:", {class_names[i]: src2tier[i] for i in src2tier})
    if "train" in ds and "test" in ds:
        wf_train, wf_test = ds["train"], ds["test"]
    else:
        sp = list(ds.values())[0].train_test_split(test_size=0.2, seed=SEED)
        wf_train, wf_test = sp["train"], sp["test"]

    # subsample easy wildfire classes (keep ALL partial); collect kept indices
    by_tier = {0: [], 1: [], 2: []}
    for i in range(len(wf_train)):
        t = src2tier.get(wf_train[i][lbl_col])
        if t is not None:
            by_tier[t].append(i)
    cap = 60 if SMOKE else CAP_EASY
    wf_keep = []
    for t in (0, 1, 2):
        idx = by_tier[t]
        random.shuffle(idx)
        keep = idx if t == 1 else idx[:cap]          # keep ALL partial; cap minimal/complete
        wf_keep += [("wf", i, t) for i in keep]
    log(">> wildfire kept per tier:", {TIERS[t]: sum(1 for s in wf_keep if s[2] == t) for t in (0, 1, 2)})

    # ---- Φ-Net ----
    log(">> loading Φ-Net Task 7 ...")
    xtr, ytr, xte, yte, recover = load_phinet()
    phi_keep = [("phi", j, int(ytr[j])) for j in range(len(ytr))]
    phi_test = [("phi", j, int(yte[j])) for j in range(len(yte))]
    if SMOKE:
        random.shuffle(phi_keep); phi_keep = phi_keep[:120]
    log(">> Φ-Net kept per tier (train):", {TIERS[t]: sum(1 for s in phi_keep if s[2] == t) for t in (0, 1, 2)})

    # ---- combined index lists ----
    combined = wf_keep + phi_keep
    random.shuffle(combined)
    n_val = max(1, int(len(combined) * (0.05 if SMOKE else 0.12)))
    val_list, train_list = combined[:n_val], combined[n_val:]
    # test = wildfire test (capped for smoke) + Φ-Net test
    wf_test_list = []
    rng = range(len(wf_test)) if not SMOKE else range(min(200, len(wf_test)))
    for i in rng:
        t = src2tier.get(wf_test[i][lbl_col])
        if t is not None:
            wf_test_list.append(("wf", i, t))
    test_list = wf_test_list + (phi_test if not SMOKE else phi_test[:60])
    counts = collections.Counter(s[2] for s in train_list)
    log(">> TRAIN tiers:", {TIERS[t]: counts[t] for t in (0, 1, 2)}, " total", len(train_list))
    log(">> VAL", len(val_list), " TEST", len(test_list),
        {TIERS[t]: sum(1 for s in test_list if s[2] == t) for t in (0, 1, 2)})

    # ---- generators (read images lazily by index; both sources -> raw RGB [0,255]) ----
    def img_wf(split, i):
        im = split[i][img_col]
        if im.mode != "RGB":
            im = im.convert("RGB")
        return np.asarray(im.resize((IMG, IMG)), dtype=np.float32)

    def make_gen(items, split_for_wf, shuffle):
        def _g():
            order = list(items)
            if shuffle:
                random.shuffle(order)
            for src, idx, t in order:
                if src == "wf":
                    yield img_wf(split_for_wf, idx), np.int32(t)
                else:
                    arr = xtr if split_for_wf is wf_train else xte
                    yield recover(arr[idx]), np.int32(t)
        return _g

    sig = (tf.TensorSpec((IMG, IMG, 3), tf.float32), tf.TensorSpec((), tf.int32))

    def ds_from(items, split_for_wf, training):
        d = tf.data.Dataset.from_generator(make_gen(items, split_for_wf, training), output_signature=sig)
        return d.batch(BATCH).prefetch(tf.data.AUTOTUNE)

    train_ds = ds_from(train_list, wf_train, True)
    val_ds = ds_from(val_list, wf_train, False)
    test_ds = ds_from(test_list, wf_test, False)

    # sqrt-dampened inverse-frequency class weights
    total = sum(counts.values())
    class_weight = {i: float((total / (3 * max(counts[i], 1))) ** 0.5) for i in range(3)}
    log(">> class weights:", class_weight)

    # ---- model: MobileNetV3-Small, baked-in preprocessing (mobile feeds raw [0,255] RGB) ----
    base = tf.keras.applications.MobileNetV3Small(
        input_shape=(IMG, IMG, 3), include_top=False, weights="imagenet",
        include_preprocessing=True, pooling="avg")
    base.trainable = False
    inp = tf.keras.Input((IMG, IMG, 3))
    x = base(inp, training=False)
    x = tf.keras.layers.Dropout(0.3)(x)
    out = tf.keras.layers.Dense(3, activation="softmax", name="tier")(x)
    model = tf.keras.Model(inp, out)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])

    cbs = [tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True)]
    e1, e2 = (1, 1) if SMOKE else (8, 6)
    log(">> stage 1: train head ...")
    model.fit(train_ds, validation_data=val_ds, epochs=e1, class_weight=class_weight, callbacks=cbs, verbose=2)
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    log(">> stage 2: fine-tune top ...")
    model.fit(train_ds, validation_data=val_ds, epochs=e2, class_weight=class_weight, callbacks=cbs, verbose=2)

    # ---- honest eval (combined test + per-domain partial) ----
    from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support
    y_true, y_pred, dom = [], [], []
    for items, split in [(test_list, wf_test)]:
        pass
    # evaluate per-example tracking domain
    def eval_items(items, split):
        yt, yp = [], []
        gen = make_gen(items, split, False)()
        batch_x, batch_y = [], []
        for xi, yi in gen:
            batch_x.append(xi); batch_y.append(int(yi))
            if len(batch_x) == BATCH:
                p = model.predict(np.stack(batch_x), verbose=0).argmax(1)
                yp += p.tolist(); yt += batch_y; batch_x, batch_y = [], []
        if batch_x:
            p = model.predict(np.stack(batch_x), verbose=0).argmax(1)
            yp += p.tolist(); yt += batch_y
        return yt, yp

    yt_all, yp_all = eval_items(test_list, wf_test)
    cm = confusion_matrix(yt_all, yp_all, labels=[0, 1, 2])
    rep = classification_report(yt_all, yp_all, labels=[0, 1, 2], target_names=TIERS, digits=3)
    acc = float(np.mean(np.array(yt_all) == np.array(yp_all)))
    prec, rec, f1, sup = precision_recall_fscore_support(yt_all, yp_all, labels=[0, 1, 2], zero_division=0)
    per_class = {TIERS[i]: {"precision": round(float(prec[i]), 3), "recall": round(float(rec[i]), 3),
                            "f1": round(float(f1[i]), 3), "support": int(sup[i])} for i in range(3)}
    n = len(yt_all)
    half = 1.96 * (acc * (1 - acc) / max(n, 1)) ** 0.5
    acc_ci = [round(max(0, acc - half), 4), round(min(1, acc + half), 4)]
    # per-domain partial recall (how well does it catch partial on real earthquake = Φ-Net test?)
    yt_phi, yp_phi = eval_items(phi_test if not SMOKE else phi_test[:60], wf_test)
    from sklearn.metrics import precision_recall_fscore_support as prf
    pp, pr, pf, ps = prf(yt_phi, yp_phi, labels=[0, 1, 2], zero_division=0)
    phi_partial = {"precision": round(float(pp[1]), 3), "recall": round(float(pr[1]), 3),
                   "f1": round(float(pf[1]), 3), "support": int(ps[1])}
    report = (f"COMBINED test accuracy: {acc:.3f} (95% CI {acc_ci}) n={n}\n\n"
              f"Confusion (rows=true,cols=pred) [{TIERS}]:\n{cm}\n\n{rep}\n"
              f"Φ-Net-only (earthquake) PARTIAL tier: {phi_partial}\n")
    log("\n================ HONEST TEST REPORT ================\n" + report)
    with open(os.path.join(OUT, "confusion_matrix.txt"), "w") as f:
        f.write(report)

    model.save(os.path.join(OUT, "keras_model.keras"))

    # ---- export TFLite ----
    log(">> exporting TFLite ...")
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    tfl = conv.convert()
    with open(os.path.join(OUT, "damage_classifier.tflite"), "wb") as f:
        f.write(tfl)
    log(f">> TFLite {len(tfl)/1e6:.1f} MB")
    with open(os.path.join(OUT, "labels.txt"), "w") as f:
        f.write("\n".join(TIERS) + "\n")
    meta = {
        "input": [1, IMG, IMG, 3], "input_range": "0..255 RGB float32 (preprocessing baked in)",
        "output": TIERS, "test_accuracy": round(acc, 4), "test_accuracy_95ci": acc_ci, "test_n": n,
        "per_class": per_class, "phinet_partial_tier": phi_partial,
        "class_weighting": "sqrt-dampened inverse-frequency",
        "advisory_only": True,
        "human_in_the_loop": "the model only SUGGESTS a tier + confidence; a human always confirms or overrides",
        "datasets": [
            "kevincluo/structure_wildfire_damage_classification (CC-BY-4.0)",
            "PEER Hub ImageNet Task 7 Damage Level (CC-BY-NC-SA-4.0)",
        ],
        "model_license": "CC-BY-NC-SA-4.0 (model weights — trained partly on PHI-Net; non-commercial/humanitarian). Beacon code is Apache-2.0.",
        "domain": "wildfire (DINS) + ground-level post-earthquake (PHI-Net). 'partial' tier now fed by PHI-Net Minor+Moderate.",
        "model_arch": "MobileNetV3Small",
    }
    with open(os.path.join(OUT, "metadata.json"), "w") as f:
        json.dump(meta, f, indent=2)

    # ---- export Core ML (best-effort) ----
    try:
        import coremltools as ct
        log(">> exporting Core ML ...")
        inp_name = model.inputs[0].name.split(":")[0]
        mlm = ct.convert(model, source="tensorflow",
                         inputs=[ct.ImageType(name=inp_name, shape=(1, IMG, IMG, 3), scale=1.0, bias=[0, 0, 0])],
                         classifier_config=ct.ClassifierConfig(TIERS),
                         minimum_deployment_target=ct.target.iOS15)
        mlm.save(os.path.join(OUT, "DamageClassifier.mlpackage"))
        log(">> Core ML saved")
    except Exception as e:
        log(f">> Core ML export skipped/failed (non-fatal): {e}")
    log(">> DONE. Artifacts in:", OUT)


if __name__ == "__main__":
    main()
