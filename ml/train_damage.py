"""
Beacon B2 — on-device advisory damage classifier.

Fine-tunes MobileNetV3-Small on the California Wildfire Structure Damage dataset
(CC-BY-4.0, HuggingFace kevincluo/structure_wildfire_damage_classification), remapped
to Beacon's mandated 3 tiers (minimal / partial / complete), and exports:
  - out/damage_classifier.tflite   (Android / LiteRT)
  - out/DamageClassifier.mlpackage  (iOS / Core ML)  [best-effort]
  - out/labels.txt, out/metadata.json, out/confusion_matrix.txt

Preprocessing is BAKED INTO the model (include_preprocessing=True) so the mobile side
only has to resize to 224x224 and feed raw [0,255] RGB — no client-side normalization.

Honest reporting: prints the REAL test accuracy + confusion matrix. No demo tuning.
"""
import json
import os
import sys

import numpy as np
import tensorflow as tf
from datasets import load_dataset

IMG = 224
BATCH = 32
SEED = 1234
OUT = os.path.join(os.path.dirname(__file__), "out")
os.makedirs(OUT, exist_ok=True)

TIERS = ["minimal", "partial", "complete"]  # index 0,1,2  == DamageTier order on mobile


def tier_of(name: str):
    """Map a wildfire DINS class name to a Beacon tier index, or None to DROP the example."""
    n = name.strip().lower().replace("-", "_").replace(" ", "_")
    if "inaccessible" in n:
        return None
    if "destroyed" in n:
        return 2  # complete
    if "minor" in n or "major" in n:
        return 1  # partial
    if "no_damage" in n or "affected" in n or "no damage" in n:
        return 0  # minimal
    # Unknown label — drop rather than mislabel.
    return None


def main():
    print(">> loading dataset (HF kevincluo/structure_wildfire_damage_classification) ...", flush=True)
    # The HF dataset card's recorded split sizes are stale (says 355; the data is actually 18,714),
    # which trips datasets' split verification. Skip the size check — the Parquet itself is complete.
    ds = load_dataset("kevincluo/structure_wildfire_damage_classification", verification_mode="no_checks")
    print(">> splits:", {k: len(v) for k, v in ds.items()}, flush=True)
    first = next(iter(ds.values()))
    print(">> features:", first.features, flush=True)

    # find the image + label columns
    img_col = next((c for c, f in first.features.items() if f.__class__.__name__ == "Image"), None)
    lbl_col = next((c for c, f in first.features.items() if f.__class__.__name__ == "ClassLabel"), None)
    if img_col is None or lbl_col is None:
        # fall back to common names
        img_col = img_col or ("image" if "image" in first.features else list(first.features)[0])
        lbl_col = lbl_col or ("label" if "label" in first.features else list(first.features)[-1])
    class_names = first.features[lbl_col].names if hasattr(first.features[lbl_col], "names") else None
    print(f">> image col = {img_col}  label col = {lbl_col}", flush=True)
    print(f">> source class names = {class_names}", flush=True)

    # name -> tier mapping (by NAME, robust to ordering)
    src2tier = {}
    for i, nm in enumerate(class_names or []):
        src2tier[i] = tier_of(nm)
    print(">> class -> tier:", {(class_names[i] if class_names else i): src2tier.get(i) for i in src2tier}, flush=True)

    # build train/val/test from available splits
    if "train" in ds and "test" in ds:
        train_split, test_split = ds["train"], ds["test"]
    else:
        only = list(ds.values())[0].train_test_split(test_size=0.2, seed=SEED)
        train_split, test_split = only["train"], only["test"]
    # carve a val set out of train
    tv = train_split.train_test_split(test_size=0.12, seed=SEED)
    train_split, val_split = tv["train"], tv["test"]
    print(f">> sizes train={len(train_split)} val={len(val_split)} test={len(test_split)}", flush=True)

    def gen(split):
        def _g():
            for ex in split:
                t = src2tier.get(ex[lbl_col])
                if t is None:
                    continue
                im = ex[img_col]
                if im.mode != "RGB":
                    im = im.convert("RGB")
                im = im.resize((IMG, IMG))
                yield np.asarray(im, dtype=np.float32), np.int32(t)
        return _g

    sig = (tf.TensorSpec((IMG, IMG, 3), tf.float32), tf.TensorSpec((), tf.int32))

    def make_ds(split, training):
        d = tf.data.Dataset.from_generator(gen(split), output_signature=sig)
        if training:
            d = d.shuffle(2048, seed=SEED)
        return d.batch(BATCH).prefetch(tf.data.AUTOTUNE)

    train_ds = make_ds(train_split, True)
    val_ds = make_ds(val_split, False)
    test_ds = make_ds(test_split, False)

    # class weights (the DINS set is imbalanced) — computed from a scan of the train labels
    counts = np.zeros(3, dtype=np.int64)
    for ex in train_split:
        t = src2tier.get(ex[lbl_col])
        if t is not None:
            counts[t] += 1
    print(">> train tier counts:", dict(zip(TIERS, counts.tolist())), flush=True)
    total = counts.sum()
    # sqrt-dampened inverse-frequency: lifts the rare 'partial' tier so the model actually
    # predicts it, WITHOUT the raw inverse-frequency overshoot (a ~15x weight made the model
    # cry 'partial' on ~1-in-4 truly-minimal buildings — recall up but precision ~0.1).
    class_weight = {i: float((total / (3 * max(counts[i], 1))) ** 0.5) for i in range(3)}
    print(">> class weights (sqrt-dampened):", class_weight, flush=True)

    # model: MobileNetV3-Small w/ baked-in preprocessing (mobile feeds raw [0,255] RGB)
    base = tf.keras.applications.MobileNetV3Small(
        input_shape=(IMG, IMG, 3), include_top=False, weights="imagenet",
        include_preprocessing=True, pooling="avg",
    )
    base.trainable = False
    inp = tf.keras.Input((IMG, IMG, 3))
    x = base(inp, training=False)
    x = tf.keras.layers.Dropout(0.3)(x)
    out = tf.keras.layers.Dense(3, activation="softmax", name="tier")(x)
    model = tf.keras.Model(inp, out)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])

    cbs = [tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True)]
    print(">> stage 1: train head ...", flush=True)
    model.fit(train_ds, validation_data=val_ds, epochs=8, class_weight=class_weight, callbacks=cbs, verbose=2)

    # stage 2: unfreeze the top of the base + fine-tune at a low LR
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
                  loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    print(">> stage 2: fine-tune top ...", flush=True)
    model.fit(train_ds, validation_data=val_ds, epochs=6, class_weight=class_weight, callbacks=cbs, verbose=2)

    # honest evaluation on the held-out test set
    from sklearn.metrics import classification_report, confusion_matrix
    y_true, y_pred = [], []
    for xb, yb in test_ds:
        p = model.predict(xb, verbose=0)
        y_pred.extend(p.argmax(1).tolist())
        y_true.extend(yb.numpy().tolist())
    cm = confusion_matrix(y_true, y_pred, labels=[0, 1, 2])
    rep = classification_report(y_true, y_pred, labels=[0, 1, 2], target_names=TIERS, digits=3)
    acc = float(np.mean(np.array(y_true) == np.array(y_pred)))
    from sklearn.metrics import precision_recall_fscore_support
    prec, rec, f1, sup = precision_recall_fscore_support(y_true, y_pred, labels=[0, 1, 2], zero_division=0)
    per_class = {TIERS[i]: {"precision": round(float(prec[i]), 3), "recall": round(float(rec[i]), 3),
                            "f1": round(float(f1[i]), 3), "support": int(sup[i])} for i in range(3)}
    n_test = len(y_true)
    half = 1.96 * (acc * (1 - acc) / max(n_test, 1)) ** 0.5  # 95% normal-approx CI on overall accuracy
    acc_ci = [round(max(0.0, acc - half), 4), round(min(1.0, acc + half), 4)]
    report = f"Test accuracy: {acc:.3f}  (95% CI {acc_ci})  n={n_test}\n\nConfusion matrix (rows=true, cols=pred) [{TIERS}]:\n{cm}\n\n{rep}\n"
    print("\n================ HONEST TEST REPORT ================\n" + report, flush=True)
    with open(os.path.join(OUT, "confusion_matrix.txt"), "w") as f:
        f.write(report)

    # Save the Keras model so Core ML / TFLite can be re-exported later WITHOUT re-training.
    model.save(os.path.join(OUT, "keras_model.keras"))

    # ---- export TFLite (float32; small model, simplest correct mobile inference) ----
    print(">> exporting TFLite ...", flush=True)
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]  # dynamic-range int8 weights, float activations
    tfl = conv.convert()
    with open(os.path.join(OUT, "damage_classifier.tflite"), "wb") as f:
        f.write(tfl)
    print(f">> TFLite size: {len(tfl)/1e6:.1f} MB", flush=True)

    with open(os.path.join(OUT, "labels.txt"), "w") as f:
        f.write("\n".join(TIERS) + "\n")
    meta = {
        "input": [1, IMG, IMG, 3],
        "input_range": "0..255 RGB float32 (preprocessing baked in)",
        "output": TIERS,
        "test_accuracy": round(acc, 4),
        "test_accuracy_95ci": acc_ci,
        "test_n": n_test,
        "per_class": per_class,
        "class_weighting": "sqrt-dampened inverse-frequency, applied during both training stages",
        "advisory_only": True,
        "human_in_the_loop": "the model only SUGGESTS a tier + confidence; the reporter (and later an analyst) always confirms or overrides",
        "domain_caveat": "Trained on California wildfire structure-damage photos (DINS). Accuracy on other hazards (earthquake, flood, conflict) is unvalidated — known domain shift. The suggestion is advisory and always human-confirmed.",
        "model": "MobileNetV3Small",
        "dataset": "kevincluo/structure_wildfire_damage_classification (CC-BY-4.0)",
    }
    with open(os.path.join(OUT, "metadata.json"), "w") as f:
        json.dump(meta, f, indent=2)

    # ---- export Core ML (best-effort) ----
    try:
        import coremltools as ct
        print(">> exporting Core ML ...", flush=True)
        # The ImageType name MUST match the model's actual input placeholder (Keras 3 auto-names it,
        # e.g. "input_layer_1") — hardcoding "image" makes coremltools fail to bind the input.
        inp_name = model.inputs[0].name.split(":")[0]
        mlm = ct.convert(model, source="tensorflow",
                         inputs=[ct.ImageType(name=inp_name, shape=(1, IMG, IMG, 3), scale=1.0, bias=[0, 0, 0])],
                         classifier_config=ct.ClassifierConfig(TIERS),
                         minimum_deployment_target=ct.target.iOS15)
        mlm.save(os.path.join(OUT, "DamageClassifier.mlpackage"))
        print(">> Core ML saved", flush=True)
    except Exception as e:
        print(f">> Core ML export skipped/failed (non-fatal): {e}", flush=True)

    print(">> DONE. Artifacts in:", OUT, flush=True)


if __name__ == "__main__":
    main()
