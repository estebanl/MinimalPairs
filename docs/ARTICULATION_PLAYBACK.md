# Illustrative articulation playback

Status: opt-in scaffolding, **inactive**. `ReviewedArticulations.forPhoneme` currently returns null. All legacy static references remain active. No new mesh is approved merely because the loader/tests accept it.

## Asset contract

`ArticulationSpec` is a typed app manifest: `modelFamily`, `phoneme`, ordered `phases` (`name`, `assetPath`, `transitionToNextMs`), exporter `topologyHash`, and `reviewStatus`.

- Paths are under `files/anatomy/` and end in `.vtlm`.
- VTLM v1 and current appended material IDs are retained. Every endpoint must use the same surface order, material IDs, vertex counts, face counts and index order. Decimate once before authoring endpoints.
- Actual endpoint topology is compared by the loader. The exporter hash is provenance metadata, not trusted validation or a checked cryptographic digest.
- All coordinates use the existing fixed decoder transform. Never normalize individual endpoints.
- Transition durations are 50–5000 ms; the final phase duration is zero. A one-phase reference is static and has no Play button.
- Labels: closure/release for stops, closure/friction for affricates, onset/target for glides, target for steady positions. Durations are illustrative and not synchronized with speech audio.
- Positions interpolate linearly; normals are interpolated then normalized with an explicit antiparallel fallback. This is not evidence of physical articulation accuracy. Contacts/intersections must be validated throughout transitions, not just at endpoints.

## Runtime behavior

The card reads the registry, uses an accepted clip when available, and otherwise retains the legacy reference. Failed loads display a retry action with static fallback. Experimental clips are never enabled. BodyParts3D source attribution is added for the new model family; existing VocalTractLab credits remain available. Preserve licensing/provenance for any retained or derived VTL geometry or pose guidance.

`rememberArticulationPlayback` owns async loading and frame ticks. Selection changes replace state synchronously, cancel the old coroutine and prevent a stale model appearing under a new sound. Generation tokens also reject old load/tick callbacks. Pause/reset/replay invalidate ticks; background ON_STOP pauses without automatic resume. A pause while loading does not discard the load.

Rendering uses sequential collection of the latest elapsed time, with backpressure, rather than cancelling an in-flight raster every animation frame. A completed render may be retained only within the same sound/model/playback generation/cutaway selection. Timing never depends on microphone input. Animation does not write to recordings or scoring.

## Release gates and validation

1. Approve full bilateral geometry and all authored targets/transitions for shape, contact, airway and educational interpretation.
2. Add resources, source/provenance notices and reviewed specs deliberately. Do not mark experimental assets accepted to bypass visual review.
3. Run Gradle common/JVM/UI and platform checks. Test rapid sound changes, play/pause/reset, retry, background/resume, rotation/cutaway and recording independence.
4. Measure actual device performance, memory and accessibility. No phone claims can be inferred from cloud JVM timings.
5. Independent anatomical/phonetic review is still required for clinical/validated claims.

Pure Kotlin regression tests can also be run with `python scripts/vtl/test_articulation_playback.py` when the official Kotlin compiler/JUnit jars are locally provisioned. This executes the actual current decoder/type code but does **not** compile Compose or replace the normal Gradle build. On 2026-10-29, an initial Java network failure was resolved using the current per-process proxy. The official Gradle9.8.1 distribution was downloaded and verified against its official SHA256, and plugin dependencies resolved. Full Gradle validation nevertheless remained blocked: the build daemon repeatedly disappeared during project configuration, before compilation/tests, including a one-worker bounded-heap retry. No test failure or definitive cause was established. Native device/UI validation remains outstanding.

## Authored section mode

A reviewed atlas spec must explicitly choose `ArticulationClipMode.AUTHORED_SECTIONS` when its bilateral mesh includes its own midline tissue surfaces. The renderer then calls `cutaway(authoredSections = true)`: every part, including the tongue, is clipped to the selected half-space; existing authored section surfaces are retained; no procedural tongue cap is synthesized, and no complete-tongue fallback occurs. Zero-area faces produced by a plane intersection are dropped. Materials, source-surface metadata and normals are preserved on valid triangles.

The default `LEGACY_FULL_TONGUE` preserves the old full-tongue reference behavior. Generated and authored section options are mutually exclusive. Animation frame identity includes the clipping side, so crossing yaw zero cannot reuse an opposite-half frame. Authored caps must be constructed and audited for both sides; the clipper does not infer missing anatomy.

Because VTLM v1 has no section flag, authored specs also carry `sectionTriangleIndices` in decoded triangle order. These indices must be unique, in bounds, and nonempty for authored-section mode. The loader verifies the marked triangles remain on runtime X=0 (within 0.00001) in every endpoint and marks them `isSection` for raster depth-tie handling. The model exporter provides this list from its audited per-triangle metadata. A single authored cap is retained for either viewing side; do not duplicate opposing coplanar caps. No undocumented heuristic guesses section identity from material or position.

Declared section vertices accepted within the small midline tolerance are canonicalized to exact X=0, then checked again for nondegeneracy. This ensures the same cap survives either retained half-space instead of a tiny exporter offset hiding it from one side.
