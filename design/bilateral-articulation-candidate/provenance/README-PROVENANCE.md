# BodyParts3D head subset (research import, 2026-10-29)
Source: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/download.html
Official current license: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html (last updated2025-02-27), states CC BY4.0. Snapshot: LICENSE-source-2026-10-29.html.
Required attribution: BodyParts3D, © The Database Center for Life Science licensed under CC Attribution4.0 International. License URL: https://creativecommons.org/licenses/by/4.0/
Indicate modifications (e.g. isolated head subset, clipped, smoothed, remeshed, recolored, rigged) and avoid endorsement claims. Preserve original asset notices. OBJ comments contain legacy CC BY-SA2.1 Japan notices; current linked publisher license differs, so preserve provenance rather than erase old notices. No share-alike is stated on current license page. This is source license research, not legal advice.

Files in head-obj are extracted byte-exact from official OBJ ZIPs using HTTP byte ranges and CRC verification. No executable or Blender addon was installed. Full ZIPs may still be partial while downloading; use individual verified OBJ files. selected_parts.tsv maps part-of concepts to FJ filenames; selected_isa_parts.tsv maps ISA concepts to FJ filenames. Many concepts share a mesh: deduplicate FJ IDs; do not import repeated rows as duplicates. BP IDs from parts_list tables are NOT OBJ filenames. Source OBJ coordinates should preserve inter-part alignment.

These are static CAD anatomical atlas meshes, not a speech rig. Anatomical completion/retopology, cavity surfaces, clipping caps, textures, lip/face surfaces and pose validation are still required. Reduced meshes can be coarse; no claim of clinical validation or speech-position accuracy. Z-Anatomy was not incorporated; it has additional derivative licensing and NC components.

Coordinate verification: +X patient left, -Y anterior, +Z superior; dimensions consistent with millimetres. Tongue bounds x[-20,18.8],y[-169.9,-110.5],z[1431,1467.3]. Preserve shared origin. Official publisher update-history explicitly confirms license update2025-02-27: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/update.html
