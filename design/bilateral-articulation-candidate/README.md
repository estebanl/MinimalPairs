# Bilateral articulation candidate — NOT RELEASE APPROVED

This directory contains reproducible development artwork and **actual Kotlin CPU-rasterizer** diagnostic images, not screenshots of a validated mobile app. No candidate is registered in the app's reviewed articulation registry. Original production VTLM resources remain unchanged.

## Geometry and provenance

- Full bilateral skin and 28 teeth are from BodyParts3D source OBJ files, not mirrored half-source anatomy. They have been cropped, simplified, and given an illustrative speaking aperture. Tooth roots are hidden for the educational cutaway; crown/gingival boundaries are authored approximations.
- Tongue, lining, lips, hard/soft palate, floor, epiglottis and section surfaces are artist-authored, atlas-guided educational approximations. Those soft tissues are symmetrized, not measured patient anatomy.
- The nasal compartment is simplified; turbinate details have been omitted after they became misleading slit artifacts at the current resolution. Do not market this as a comprehensive anatomy atlas.
- BodyParts3D, © The Database Center for Life Science, licensed under CC Attribution 4.0 International. Publisher license: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html . License: https://creativecommons.org/licenses/by/4.0/ . Original source notices and acquisition evidence remain in `/workspace/shared/bodyparts3d-research` and prior atlas review records; this candidate does not overwrite them.
- Retained VocalTractLab production assets and any VTL-derived work retain their separate GPL-3.0 provenance. This notice does not relicense those assets or code.

## Reproduction

From repository root:

1. `blender -b -t 4 --python scripts/vtl/production_cutaway_base.py` (authoring precursor; source atlas required).
2. `blender -b -t 4 --python scripts/vtl/build_bilateral_candidate.py`.
3. `blender -b -t 4 --python scripts/vtl/export_bilateral_basis.py`.
4. `python scripts/vtl/render_bilateral_diagnostic.py` (local Kotlin compiler/JUnit dependencies required as documented in script).

The exporter decimates and triangulates **once**. `bilateral-topology.json` records immutable correspondence, material groups, transforms, display-section flags, source hash, crop-plane snaps, and skin-envelope clamping. VTLM v1 itself does not encode display-section flags; accepted app specifications must explicitly carry the section triangle indices. Midline display caps are single surfaces at runtime x=0, retained on either side, with no duplicate opposite-side caps.

`kotlin-*.png` are renders of decoded VTLM using application renderer code. Neutral background is a display composite; `*-argb.png` preserves original transparent pixels. Legacy and authored-section cut modes are not equivalent; only authored-section all-part clipping is appropriate for this candidate.

## Pose files

`poses/` and `pose-previews/` are UNREVIEWED experiments. They may be **stale relative to the latest base** while visual corrections are being made. Do not copy them into application resources. The manifest's source hash and fixed-topology hash must match a newly accepted basis before regeneration/use. Matching topology and nonzero normals do not establish valid phonetic contacts or intermediate motion.

## Unresolved release gates

- Visual acceptance of front, both sagittal cutaways, oblique views and actual mobile-sized UI.
- Clean gingival/crown relationships and all crop boundaries; no overlapping or exposed roots.
- Physically credible lip/tongue/palate contacts, continuously open intended airways, and no self/inter-surface intersections across transition samples.
- Independent phonetics/anatomy review for all supported sounds; shared voicing-pair mouth shapes must not imply that voicing is visibly demonstrated.
- Fresh complete Compose/native builds, app lifecycle and accessibility checks, actual iOS/Android device rendering, frame time, memory and battery measurements.
- Accepted assets and explicit display-section metadata wired into the reviewed registry, with rollback and resource checks.

Cloud diagnostic timings are not phone performance evidence. Static previews and standalone unit tests are not a production-readiness claim.

### Current numerical/geometry caveats

The fixed basis uses constrained triangulation for authored midline sections. Export metadata records every deterministic near-zero triangle removal. Those removals can leave microscopic omissions: **watertight topology is not established**. A successful float32 guard means a file can be decoded safely; it does not imply a closed anatomical volume. Some lateral tongue endcap triangles and intermediate target transitions are still being refined. `contact-audit.json` distinguishes roof clearance, gingiva/tooth interval collisions, and midline lip rays; a clean sampled screen is not a continuous surface or airway proof.
