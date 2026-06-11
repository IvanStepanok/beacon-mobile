# Beacon — on-device advisory damage classifier (B2)

The bundled `shared/src/androidMain/assets/damage_classifier.tflite` is a **MobileNetV3-Small**
fine-tuned to advise the 3-tier damage grade (minimal / partial / complete). It runs **on-device,
fully offline** (LiteRT on Android; Core ML on iOS — roadmap). Its output is **advisory only**: it
pre-highlights a tier for the reporter to confirm or change, and abstains below a 0.55 confidence
floor. The human grade is always authoritative (the brief's anti-"solely generative AI" stance).

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
  minimal       0.942    0.977     1758    (intact — reliable)
  partial       0.500    0.022       91    (rare + ambiguous — the model usually defers to a
                                            neighbouring tier; this is why it's advisory)
  complete      0.974    0.986     1887    (destroyed — reliable)
```

The classifier is reliable at the extremes (intact vs destroyed, ~96–99%); the intermediate
"partial" tier is genuinely hard and under-represented, so the model tends to defer — and the
reporter confirms. On-device spot checks: a destroyed structure → `complete` @ 99.96%; a
borderline lightly-scorched structure → `minimal` @ 0.538 (below floor → abstains, no suggestion).

## Reproduce

```bash
uv venv --python 3.12 .venv && source .venv/bin/activate
uv pip install "tensorflow>=2.16,<2.18" datasets pillow numpy scikit-learn coremltools
python train_damage.py   # → out/damage_classifier.tflite (+ Core ML), labels.txt, confusion_matrix.txt
```

Then copy `out/damage_classifier.tflite` into `shared/src/androidMain/assets/`. The model bundles
its own preprocessing, so the app feeds a 224×224 RGB image as float [0,255] — no client-side
normalization. Output is a 3-way softmax in tier order [minimal, partial, complete].

> Runtime note: the model is exported by TF 2.17 (uses `FULLY_CONNECTED` v12), so the Android app
> uses the **LiteRT 1.0.1** runtime — the older `org.tensorflow:tensorflow-lite:2.16.x` cannot load it.
