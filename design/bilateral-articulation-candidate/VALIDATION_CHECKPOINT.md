# Review checkpoint — 2026-10-29 22:19 UTC

Not release approved. No candidate assets are active in production.

- Fixed base: **10,259 triangles, 563,532 bytes**; SHA256 `374f745f6a18f1c7934ecd718be76b2e4e992d0ba1b1204a49c3439f9dd9b626`.
- 23 endpoint files map to 29 selected phonemes; voiced/unvoiced pairs share visible mouth geometry, not voicing demonstration.
- Every endpoint passes the float32 numeric guard; all five transition families pass 51 interpolated frames each for finite, nondegenerate geometry and usable normals.
- The tongue sagittal display section preserves all 84 original boundary points. Fixed fan connectivity and per-endpoint visibility-kernel anchor pass 23 endpoints and five transitions ×101 frames with positive areas.
- Actual Kotlin decoder/rasterizer views and both-side half-plane checks pass for the base. Full target-loader results are recorded separately when available.
- Independent sparse screens find no >0.1mm gingiva/tooth interval overlaps in any of the 23 endpoints. These are sample screens, not mesh boolean proofs.
- Bilabial closure has no open front rays at nine sampled lateral positions from−1.6cm to+1.6cm. This does not establish a continuous watertight seal.
- Alveolar and affricate closure contact is observed in sampled mucosal geometry. Velar contact is within a 0.1mm screening tolerance; the closest sampled point has **−0.072mm gap (slight overlap)**. Do not describe this as exact nonpenetrating contact.

## Still required

Independent visual/phonetic/anatomical acceptance, continuous collision and airway verification, final app UI review, full platform builds and real Android/iOS device performance and lifecycle testing. Artist-authored tissue and speech constraints are educational approximations, not measured or clinically validated anatomy.

Other section surfaces still contain microscopic deterministic triangle removals listed in export metadata; the restored tongue fan must not be generalized into a claim that every volume is watertight.

## Evidence

`pose-numeric-audit.json`, `transition-numeric-audit.json`, `fan-kernel-audit.json`, `contact-audit.json`, `velar-lip-seal-audit.json`, `base-numeric-audit.json`, `kotlin-diagnostic.json`, and actual renders in `pose-previews/`.

`poses/manifest.json.sourceBasisSHA256` hashes **bilateral-topology.json**, not the binary VTLM. The topology JSON separately records `vtlm_sha256` and authoring input hash. Current manifest and topology hash match at this checkpoint.
