#!/usr/bin/env bash
# Build, audit, publish publicly, then update the existing Azure Function app.
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
scorer_python="${SCORER_PYTHON:-$project_dir/build/phoneme-model-venv/bin/python}"
scorer_repository="estebanlopez/minimalpairs-scorer"
scorer_tag="${SCORER_IMAGE_TAG:-$(date -u +%Y%m%dT%H%M%SZ)}"
scorer_image="$scorer_repository:$scorer_tag"
scorer_report="$project_dir/build/reports/dockerhub/$scorer_tag"
scorer_scanner="aquasec/trivy@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c"
mkdir -p "$scorer_report"
docker buildx build --platform linux/amd64 --provenance=false --load \
    -f backend/Dockerfile.azure -t "$scorer_image" backend
docker save -o "$scorer_report/image.tar" "$scorer_image"
"$scorer_python" scripts/audit_public_scorer_image.py "$scorer_report/image.tar" \
    --report "$scorer_report/layer-audit.json"
docker pull "$scorer_scanner"
docker run --rm --network none -v "$scorer_report:/scan" "$scorer_scanner" \
    image --input /scan/image.tar --scanners secret --offline-scan \
    --skip-db-update --skip-java-db-update --skip-check-update --skip-version-check \
    --timeout 10m --exit-code 1 --format json --output /scan/trivy-secrets.json
docker push "$scorer_image"
scorer_digest="$(docker image inspect "$scorer_image" --format '{{index .RepoDigests 0}}')"
[[ "$scorer_digest" == "$scorer_repository@sha256:"* ]]
az containerapp update -g minimalpairs-scorer-rg -n minimalpairs-scorer \
    --image "docker.io/$scorer_digest" \
    --query '{name:name,state:properties.provisioningState,url:properties.configuration.ingress.fqdn}' -o json
scorer_ready=false
for ((attempt=0; attempt<60; attempt++)); do
    scorer_ready="$(az containerapp show -g minimalpairs-scorer-rg -n minimalpairs-scorer \
        --query 'properties.latestRevisionName == properties.latestReadyRevisionName' -o tsv --only-show-errors)"
    [[ "$scorer_ready" == true ]] && break
    sleep 5
done
[[ "$scorer_ready" == true ]] || { echo 'Azure revision did not become ready; audit files retained.' >&2; exit 1; }
"$scorer_python" scripts/configure_azure_scorer_scaling.py --cooldown 60
"$scorer_python" scripts/azure_speak.py verify
# Keep the small audit reports; the local Docker image remains available.
rm "$scorer_report/image.tar"
