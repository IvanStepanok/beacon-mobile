# Beacon — on-device advisory damage classifier (B2)

A single **MobileNetV3-Small** advises the 3-tier damage grade (minimal / partial / complete),
running **on-device, fully offline** on both platforms from the same trained weights:
- Android: `shared/src/androidMain/assets/damage_classifier.tflite` (LiteRT runtime).
- iOS: `iosApp/iosApp/DamageClassifier.mlmodelc` (Core ML via Vision `VNCoreMLRequest`).

Its output is **advisory only**: it pre-highlights a tier for the reporter to confirm or change, and
abstains below a 0.55 confidence floor. The human grade is always authoritative (the brief's
anti-"solely generative AI" stance). Verified end-to-end on both a destroyed structure (→ complete
@ ~99.9% on Android and iOS) and a borderline one (→ minimal below the floor → abstains).

## Dataset & license

- **California Wildfire Structure Damage Classification** — ground-level, per-structure photos from
  Cal Fire DINS damage assessments.
  - HuggingFace: `kevincluo/structure_wildfire_damage_classification`
  - Zenodo: https://zenodo.org/records/8336570 · Paper: https://www.mdpi.com/2571-6255/7/4/133
  - **License: CC-BY-4.0** (commercial + redistribution OK with attribution; source is Cal Fire
    DINS public-record data). Attribution: Cal Fire DINS + the dataset authors.
- The 6 source classes are remapped to Beacon's 3 tiers:
  `no_damage`,`affected` → **minimal** · `minor`,`major` → **partial** · `destroyed` → **complete**
  (`inaccessible` dropped).

**Known limitation (honest):** the domain is US wildfire structures, not earthquake/conflict damage.
Visual cues differ (fire char vs. structural cracks/collapse). Mitigated by the advisory framing +
confidence floor + human-in-the-loop. To broaden hazard coverage, blend MEDIC (CrisisNLP,
severe/mild/none) — note it is CC-BY-NC-SA + research-only terms.

## Honest test metrics (held-out 3,736 images, no demo tuning)

```
Test accuracy: 0.958
              precision  recall   support
  minimal       0.944    0.975     1758    (intact — reliable)
  partial       0.600    0.033       91    (rare + ambiguous — the model usually defers to a
                                            neighbouring tier; this is why it's advisory)
  complete      0.972    0.987     1887    (destroyed — reliable)
```
(full report: `confusion_matrix.txt`)

The classifier is reliable at the extremes (intact vs destroyed, ~96–99%); the intermediate
"partial" tier is genuinely hard and under-represented, so the model tends to defer — and the
reporter confirms. On-device spot checks (same model): a destroyed structure → `complete` @
99.96% (Android) / 99.95% (iOS); a borderline lightly-scorched structure → `minimal` below the
floor → abstains, no suggestion.

## Reproduce

```bash
uv venv --python 3.12 .venv && source .venv/bin/activate
uv pip install "tensorflow>=2.16,<2.18" datasets pillow numpy scikit-learn coremltools
python train_damage.py   # → out/damage_classifier.tflite (+ Core ML), labels.txt, confusion_matrix.txt
```

`train_damage.py` writes `out/` : `damage_classifier.tflite`, `DamageClassifier.mlpackage`,
`keras_model.keras` (so Core ML / TFLite can be re-exported without retraining), `labels.txt`,
`confusion_matrix.txt`. The model bundles its own preprocessing, so the app feeds a 224×224 RGB
image as float [0,255] — no client-side normalization. Output is a 3-way softmax in tier order
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
> uses the **LiteRT 1.0.1** runtime — the older `org.tensorflow:tensorflow-lite:2.16.x` cannot load it.
> iOS loads `DamageClassifier.mlmodelc` from the bundle and runs it through Vision (`VNCoreMLRequest`,
> ScaleFill to match training).
