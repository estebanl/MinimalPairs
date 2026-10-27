#!/usr/bin/env python3
"""Explicitly download the pinned optional checkpoint; never called by the API."""

import argparse
import hashlib
import json
from pathlib import Path

REPOSITORY = "facebook/wav2vec2-xlsr-53-espeak-cv-ft"
REVISION = "2c733782da5604684829819a5eb744c193fe9398"
FILES = ["config.json", "preprocessor_config.json", "vocab.json", "pytorch_model.bin",
         "special_tokens_map.json", "tokenizer_config.json", "README.md"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("build/models/phoneme-ctc"))
    args = parser.parse_args()
    from huggingface_hub import snapshot_download

    snapshot_download(repo_id=REPOSITORY, revision=REVISION, local_dir=args.output,
                      allow_patterns=FILES, max_workers=2)
    hashes = {}
    for name in FILES:
        path = args.output / name
        with path.open("rb") as source:
            hashes[name] = hashlib.file_digest(source, "sha256").hexdigest()
    manifest = {"repository": REPOSITORY, "revision": REVISION, "sha256": hashes,
                "license": "Apache-2.0, per the pinned upstream model card"}
    (args.output / "provisioning.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Provisioned {REPOSITORY}@{REVISION} in {args.output}")


if __name__ == "__main__":
    main()
