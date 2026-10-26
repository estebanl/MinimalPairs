# VocalTractLab model preview

This contact sheet documents the six models bundled in the app.

Open `preview.svg` for a contact sheet of the actual exported JD3 geometry.
The binary indexed meshes retain the source vertices, visible triangle indices, and
upstream corner normals. Exported surfaces include tongue, upper/lower covers,
dental arches, lips, uvula, epiglottis, and cheek covers. Each pose has 1,811
vertices and 2,804 triangles. The preview applies a midsagittal clip and a fixed
camera transform; it does not reshape the anatomy. The displayed surfaces are
vocal-tract boundaries, not a full external face or solid tongue volume.

| App vowel | Upstream SAMPA shape | Asset |
| --- | --- | --- |
| iː | i | fleece.vtlm |
| ɪ | I | kit.vtlm |
| e (DRESS) | E | dress.vtlm |
| ʊ | U | foot.vtlm |
| uː | u | goose.vtlm |
| ɔː | O | thought.vtlm |

These are representative speaker presets, not validated British-English vowel
realizations. Duration is not part of a static mesh. Exact imported presets for
æ, ɑː, ʌ, and ɒ are not available in the selected source. The app
uses the existing illustrations for those sounds rather than inventing poses.
Descriptive filenames deliberately avoid SAMPA's case-sensitive i/I and u/U
pairs, which collide on common case-insensitive filesystems.

The app uses smooth corner normals, geometric midsagittal clipping, and a fixed
transform shared by all poses. Hidden filling triangles are omitted following
the upstream viewer's convention. Rotation, zoom, and cutaway controls remain
available. The SVG is a simpler flat-shaded inspection view.

Source, license, and provenance: `../../third_party/vocaltractlab/README.md`.
`manifest.json` records the pinned source revision and SHA-256 hashes.
To regenerate, extract the source archive to a temporary directory and run:

```sh
bash scripts/vtl/regenerate.sh /path/to/extracted/upstream
```
