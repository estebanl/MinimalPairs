# Reproduce the disabled review candidate

Requirements: Blender4.3.x, Python3 with NumPy, the exact source OBJ subset listed in `provenance/head-mesh-manifest.json`, and Kotlin/JVM tooling for renderer checks. The official BodyParts3D download/license URLs and source checksums are in `provenance/README-PROVENANCE.md`. Preserve original OBJ license comments. No credentials are needed for public source data.

Three separate native review bundles keep large editable files out of Git:

1. **Source subset**: byte-exact OBJ files, manifest and publisher license snapshot.
2. **Editable authoring**: `production-base.blend`, `bilateral-base.blend`, `bilateral-lod.blend`, and the fixed topology JSON. Restore their paths under `design/` as recorded in the ZIP.
3. **Runtime/review**: all23 VTLM endpoints,23 corresponding static GLBs, a neutral Blender review scene, manifest, audits and previews. GLBs are review exports, not the application format.

Set `BP3D_SOURCE` to the extracted source subset root (containing `head-obj/`). From repository root:

```sh
# Start from the editable production-base.blend checkpoint, or recreate it:
# blender -b --python scripts/vtl/atlas_anatomy_review.py
# blender -b --python scripts/vtl/atlas_add_skin.py
# blender -b --python scripts/vtl/production_cutaway_base.py
blender -b --python scripts/vtl/build_bilateral_candidate.py
blender -b --python scripts/vtl/export_bilateral_basis.py
python scripts/vtl/generate_articulation_candidates.py
python scripts/vtl/audit_candidate_vtlm.py design/bilateral-articulation-candidate/poses/*.vtlm
python scripts/vtl/audit_articulation_transitions.py
python scripts/vtl/audit_articulation_contacts.py
python scripts/vtl/audit_velar_and_lip_seal.py
python scripts/vtl/audit_tongue_fan_kernel.py
python scripts/vtl/render_bilateral_diagnostic.py
python scripts/vtl/render_articulation_candidates.py
blender -b --python scripts/vtl/export_articulation_review.py
```

Renderer scripts currently expect Kotlin compiler JARs under `/tmp/minimalpairs-kotlinc`; inspect their command construction and supply matching compiler dependencies. Full Gradle build remains the authoritative application compile check; standalone diagnostic compilation is not a substitute.

Do not re-decimate individual poses. Basis export fixes group mapping, connectivity, section metadata and fan boundary indices once. Generated files remain **UNREVIEWED**, and the application registry stays empty until acceptance gates are satisfied.
