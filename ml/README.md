# Beacon — on-device advisory damage classifier (B2)

A single **MobileNetV3-Small** advises the 3-tier damage grade (minimal / partial / complete),
running **on-device, fully offline** on both platforms from the same trained weights:
- Android: `shared/src/androidMain/assets/damage_classifier.tflite` (LiteRT runtime).
- iOS: `iosApp/iosApp/DamageClassifier.mlmodelc` (Core ML via Vision `VNCoreMLRequest`).

Its output is **advisory only**: it pre-highlights a tier for the reporter to confirm or change, and
abstains below a 0.45 confidence floor. The human grade is always authoritative (the brief's
anti-"solely generative AI" stance). Verified end-to-end on both a destroyed structure (→ complete
@ ~99.9% on Android and iOS) and a borderline one (→ minimal below the floor → abstains).


## Datasets & licence

Trained on **two ground-level datasets** so the model covers both hazard look-and-feel AND the
hard middle "partial" tier:

1. **California Wildfire Structure Damage Classification** (Cal Fire DINS), ground-level per-structure photos.
   - HuggingFace: `kevincluo/structure_wildfire_damage_classification`
   - **Licence: CC-BY-4.0.** Attribution: Cal Fire DINS + dataset authors.
   - Remap: `no_damage`,`affected` → **minimal** · `minor`,`major` → **partial** · `destroyed` → **complete** (`inaccessible` dropped).
2. **PEER Hub ImageNet (Φ-Net), Task 7 "Damage Level"**, ground-level post-earthquake structural photos.
   - https://apps.peer.berkeley.edu/phi-net/ (request access). Citation: Gao & Mosalam, PEER Hub ImageNet.
   - **Licence: CC-BY-NC-SA-4.0.** Added to fill the wildfire set's near-empty middle tier.
   - Remap: `Undamaged` → **minimal** · `Minor`,`Moderate` → **partial** · `Heavy` → **complete**.
   - The `.npy` X is caffe-BGR mean-subtracted; the trainer reverses that to raw RGB to match the wildfire pipeline.

> ⚠️ **MODEL-WEIGHTS LICENCE: CC-BY-NC-SA-4.0.** Because the weights are trained partly on Φ-Net
> (NC-SA), the *bundled model files* (`damage_classifier.tflite`, `DamageClassifier.mlmodelc`) are
> **non-commercial / humanitarian-use, ShareAlike**, appropriate for the UNDP non-commercial
> mandate. **Beacon's source code stays Apache-2.0.** Do not relicense the model weights as Apache.

**Known limitation (honest):** wildfire **and** ground-level earthquake are now in-domain (validated:
partial F1 0.662 / recall 0.761 on the held-out earthquake test). Flood and conflict damage remain
**unvalidated** (domain shift). Mitigated by advisory framing + confidence floor + human-in-the-loop.

## Honest test metrics (held-out 4,234 images, wildfire + earthquake, no demo tuning)

```
COMBINED test accuracy: 0.902   (95% CI 0.893–0.911, n=4,234)   macro-F1 0.797
              precision  recall   f1     support
  minimal       0.940    0.891   0.915    1965
  partial       0.503    0.562   0.531     288    (the hard middle — now genuinely usable)
  complete      0.930    0.963   0.946    1981

Φ-Net-only (REAL earthquake) partial tier:  precision 0.586  recall 0.761  f1 0.662  (n=197)
```
(full report: `confusion_matrix.txt`; per-class metrics + 95% CI in `metadata.json`)

**Why 90.2% and not the old 94.5%:** the wildfire-only model scored 94.5% but its "partial" tier was
near-useless (F1 **0.226**). The headline was inflated by a bimodal test (almost all easy
minimal/complete, only 91 partials). Adding Φ-Net's earthquake partials makes the test **harder and
more representative** (288 partials + real earthquake photos), so the honest overall drops a little
while the metric that matters improves sharply: **partial F1 0.226 → 0.531 (0.662 on real
earthquakes), macro-F1 0.717 → 0.797.** The model only *suggests* a tier + confidence; the reporter
confirms or overrides, so a still-imperfect middle tier never decides the grade.

## Reproduce

```bash
uv venv --python 3.12 .venv && source .venv/bin/activate
uv pip install "tensorflow>=2.16,<2.18" datasets pillow numpy scikit-learn coremltools
# Download Φ-Net Task 7 (request at apps.peer.berkeley.edu/phi-net) and point at it:
PHINET_TASK7_DIR=~/Downloads/task7 OUT=out python train_damage.py   # → out/damage_classifier.tflite (+ Core ML), confusion_matrix.txt
# (Φ-Net Task 7 is required by this recipe; SMOKE=1 runs a tiny pipeline check.)
```

`train_damage.py` writes `out/` : `damage_classifier.tflite`, `DamageClassifier.mlpackage`,
`keras_model.keras` (so Core ML / TFLite can be re-exported without retraining), `labels.txt`,
`confusion_matrix.txt`. The model bundles its own preprocessing, so the app feeds a 224×224 RGB
image as float [0,255], with no client-side normalization. Output is a 3-way softmax in tier order
[minimal, partial, complete].

Ship the model:
```bash
# Android — drop the TFLite into assets:
cp out/damage_classifier.tflite ../shared/src/androidMain/assets/

# iOS — compile the Core ML package and add it to the Xcode target (idempotent):
xcrun coremlcompiler compile out/DamageClassifier.mlpackage ../iosApp/iosApp/
( cd ../iosApp && ruby add_model_to_xcode.rb )    # uses the xcodeproj gem
```

> Runtime note: the model is exported by TF 2.17 (uses `FULLY_CONNECTED` v12), so the Android app
> uses the **LiteRT 1.0.1** runtime; the older `org.tensorflow:tensorflow-lite:2.16.x` cannot load it.
> iOS loads `DamageClassifier.mlmodelc` from the bundle and runs it through Vision (`VNCoreMLRequest`,
> ScaleFill to match training).
