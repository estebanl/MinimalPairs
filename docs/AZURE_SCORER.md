# Azure phoneme scorer

The backend runs as an Azure Functions Python v2 ASGI app, hosted in Azure
Container Apps with `kind=functionapp`, in Canada Central. This is the Azure
Functions runtime, not a Uvicorn-only container. The container path accommodates
the 1.2 GB model without fitting it into the Functions ZIP package limit.

Deployed base URL:
`https://minimalpairs-scorer.nicewater-7a0bdca3.canadacentral.azurecontainerapps.io`

## Resources

- Resource group: `minimalpairs-scorer-rg`
- Function container app: `minimalpairs-scorer`
- Consumption environment: `minimalpairs-functions`
- Public image repository: [estebanlopez/minimalpairs-scorer](https://hub.docker.com/r/estebanlopez/minimalpairs-scorer)
- Standard LRS host storage: `minimalpairsscorerb80386`
- User-assigned managed identity: `minimalpairs-scorer`
- Compute: 2 vCPU / 4 GiB, minimum zero replicas, maximum one.
- Scale-to-zero cooldown: 60 seconds (previously 300); polling interval remains 30 seconds.
- Host HTTP concurrency: one executing request, four outstanding requests.

Compute can scale to zero. Host storage charges still apply while idle;
requests and active compute incur usage charges. Cold starts include container
startup and lazy model loading. The one-replica limit bounds scaling but is not
high availability and does not guarantee a particular monthly bill or latency.
No existing Function Apps were changed.

The identity uses `Storage Blob Data Owner` only on this storage account.
The public Docker Hub image can be pulled without registry credentials. The host accesses storage using identity-based
`AzureWebJobsStorage__*` settings. Registry admin credentials and storage
connection strings are not embedded in the container. Application audio is
processed in memory and is not saved to Azure Storage by the scoring code.

## API access

Both `/health` and `/v1/score/phoneme` require an Azure Functions key in the
`x-functions-key` header. An unauthenticated health request returns HTTP 401.
The API request/response contract is otherwise unchanged. The server keeps
consonant-only selection and microphone quality rejection behavior.

Discover the base hostname:

```sh
az containerapp show -g minimalpairs-scorer-rg -n minimalpairs-scorer \
  --query properties.configuration.ingress.fqdn -o tsv
```

Manage keys in Azure or with `az containerapp function keys`. Do not put a key in
source control, a URL query string, or a distributed client binary. Clients use
the HTTPS base URL and key header. The desktop launcher below fetches the key
into memory and configures the app process automatically.

## Desktop Speak connection

With Azure CLI signed in, launch the desktop app or run the rendered Speak test:

```sh
build/phoneme-model-venv/bin/python scripts/azure_speak.py run
build/phoneme-model-venv/bin/python scripts/azure_speak.py verify
build/phoneme-model-venv/bin/python scripts/azure_speak.py verify --module stories
build/phoneme-model-venv/bin/python scripts/azure_speak.py verify --module all
# Confirm zero replicas before Speak makes its first scoring request:
build/phoneme-model-venv/bin/python scripts/azure_speak.py verify --cold-start
```

The launcher reads the endpoint and key from Azure, validates access, then passes
`MINIMAL_PAIRS_SCORER_URL` and `MINIMAL_PAIRS_SCORER_KEY` in the child environment.
It never writes or prints the key, and uses a single-use Gradle daemon. The verify
mode sends bundled example PCM through Speak's recording/scoring/playback UI and
checks saved Progress after reopening an isolated Room database. It also checks
that noise rejection does not save a score. No physical microphone is activated
and retained microphone WAVs are excluded. Reports and screenshots are in
`composeApp/build/reports/articulator-azure/`.

Stories verification drives the real Stories composable and component against
Azure HTTPS scoring, checking all four reference replies, selected-vowel scores,
outcome playback, and saved progress after reopening an isolated Room database.
When `build/reports/stories/controlled/` contains the generated controls, it also
checks the four alternate endings and rejects an out-of-pair vowel without adding
a result. Silence and example endings do not add personal scores or confusion
trials. Screenshots and persisted test scores are written to
`composeApp/build/reports/stories-azure/`. This uses injected reference PCM at the
recorder boundary, not a physical microphone or the user's personal progress data.

The shared client sends `x-functions-key` only for HTTPS requests, disables
redirects in its default HTTP client, and allows 120 seconds for cold-start
requests. HTTP 401/403 produces actionable access-key guidance without saving a
score. Local unkeyed development remains supported. JVM and iOS hosts can read
the key from `MINIMAL_PAIRS_SCORER_KEY`; Android and all hosts can supply
`PhonemeScorerConfiguration.functionKey` at runtime before creating the root.
No key is stored in Info.plist, resources, source code, or build arguments.

## iPhone Speak connection

An ordinary iOS build has no scorer configured. Shell environment variables on
the Mac do not automatically reach an app launched on an iPhone. With Azure CLI
signed in, build, install, and provision the connected device with:

```sh
./scripts/run_ios.sh
# Re-provision an already installed build after rotating the Azure key:
./scripts/run_ios.sh --skip-build
```

`run_ios.sh` is an alias of `run_on_device.sh`; both enable Azure scoring by
default. Use `--no-azure-scorer` to launch with the saved connection or a custom
endpoint without looking up Azure credentials.

The launcher retrieves and validates the existing Azure endpoint and key using
the same flow as desktop. It forwards them through `DEVICECTL_CHILD_` environment
variables at launch, keeping the key out of command arguments and build outputs.
Before Compose creates its root, the iOS host saves the connection to Keychain
with `WhenUnlockedThisDeviceOnly` accessibility and supplies the shared scorer
configuration. Opening the app from its icon later restores that connection.
An explicit new endpoint replaces the entire saved connection and never reuses
the old endpoint's key. A Keychain write failure logs a credential-free warning;
the connection still works for that launch and must be provisioned again later.

Xcode launches can instead set `MINIMAL_PAIRS_SCORER_URL` and
`MINIMAL_PAIRS_SCORER_KEY` in the Run scheme's environment (keep personal schemes
unshared). The device launcher also forwards these variables when supplied by
the caller with `--no-azure-scorer`. Persisted connections require HTTPS. No
credential is embedded for TestFlight or other devices; each device needs runtime
provisioning. A distributed app still needs an end-user authentication flow.

Configuration regression checks on macOS:

```sh
bash scripts/test_ios_scorer_connection.sh
build/phoneme-model-venv/bin/python -m unittest discover -s scripts/tests -p test_azure_speak.py
```

Verified on 2026-10-27 through the rendered Speak screen and real Azure HTTP
client: bundled `pear` /p/ 83.01685, `measure` /ʒ/ 81.13979, and `rope` /p/
97.81593. Original PCM replay matched, all three scores survived reopening the
test Room database, and a noisy follow-up produced retry guidance without adding
a fourth record. The ordinary JVM suite passed 209 tests (two opt-in tests
skipped); the separate Azure live test passed. Android and iOS simulator Kotlin
compilation also passed. This verifies the transport/UI/persistence path using
controlled PCM, not a new physical microphone session or human calibration.

## Build and redeploy

Azure CLI must be signed into the subscription containing these resources, with
the `containerapp` extension installed. Docker Desktop must be running and signed
into Docker Hub as `estebanlopez`. The Python environment needs `httpx`. The script
targets the existing resources above and pins each deployed image by its digest:

```sh
./scripts/deploy_azure_scorer.sh
```

The script builds Linux/amd64 locally, exports the image, audits every filesystem
layer and its configuration against current Docker Hub and Function credentials,
and runs Trivy's secret scanner offline. Publication stops if either audit fails.
Only then does it push the public image, update Azure, wait for the new revision to become ready, and run the rendered
Speak and Stories verification. The launcher retries transient key-management startup failures. Audit reports remain under `build/reports/dockerhub/<tag>/`; the
large export is removed after successful verification. `SCORER_IMAGE_TAG` and
`SCORER_PYTHON` override the timestamp tag and Python executable.

Only allowlisted backend source, runtime configuration, dependencies, and public
model files enter the image. Personal recordings, local app data, credentials,
and model download caches are excluded. Model license and provenance are included
under `/opt/phoneme-model/notices/`. The intermediate download stage is not
published. PyTorch CPU 2.9.1 and Transformers 4.57.3 use the pinned public model
`facebook/wav2vec2-xlsr-53-espeak-cv-ft@2c733782da5604684829819a5eb744c193fe9398`.
Inference is offline and the build fails if model loading fails. The Functions
Python 3.12 base is pinned by digest; update that digest deliberately for patches.
Secret scanning reduces exposure risk but does not prove the absence of every
possible secret. Review code and build inputs before future public releases.

Azure host storage, managed identity, compute settings, URL, and Function-key
protection remain in Azure. The original Basic ACR was a separate resource from
the identically named storage account; removing the registry must never remove
that storage account or the resource group.

The deployment command uses the current Azure CLI Function-on-Container-Apps
preview integration. See [Microsoft's hosting documentation](https://learn.microsoft.com/en-us/azure/azure-functions/functions-container-apps-hosting).

## Verification

### Stories rollout (2026-10-28)

Backend 2.3.0 was published as
`estebanlopez/minimalpairs-scorer:20261028T233923Z`, digest
`sha256:d100f303e04b5bd2aebe89151c4b23fb6537713028977a3c93ae4d48dcd9e524`.
Revision `minimalpairs-scorer--0000004` receives 100% of traffic. Key protection,
the 0–1 replica limits, and the 60-second scale-to-zero cooldown were preserved.
The image loaded its pinned model successfully on Linux; full-layer and offline
secret audits passed before publication.

Both live UI tests passed against Azure. Stories verified intended and alternate
endings for all four stories, the correct outcome audio, visible scores, and eight
results surviving database reopening. Out-of-pair speech, silence, and simulated
endings did not add results. Intended reference contrast confidence was 99.0
(sheep/ship), 95.3 (pen/pan), 98.1 (heart/hut), and 94.2 (full/fool).

Evidence is in `build/reports/azure/stories-deployment.json`,
`composeApp/build/reports/stories-azure/`, and
`build/reports/dockerhub/20261028T233923Z/`. The connected iPhone was rebuilt,
installed, and provisioned with the existing Azure connection without clearing
its data. The live scoring evidence above comes from the shared rendered app
screen with controlled PCM, not a new physical microphone capture.

The physical iPhone 13 mini UI smoke test also passed: each of the four Stories
could be selected, its Record control reached, and its ungraded example ending
opened. It did not activate the microphone or add synthetic results to personal
progress. Run it again with
`scripts/run_articulator_device_tests.sh DEVICE_UDID -only-testing:ArticulatorDeviceTests/ArticulatorDeviceTests/testStoriesCatalogAndExampleEndings`.
The successful result bundle is
`build/ArticulatorDeviceTests/20261028T235144Z.xcresult`.

### General service checks

```sh
build/phoneme-model-venv/bin/python -m pip install -r backend/requirements-azure.txt
(cd backend && PYTHONDONTWRITEBYTECODE=1 ../build/phoneme-model-venv/bin/python -m pytest tests)
build/phoneme-model-venv/bin/python scripts/verify_azure_scorer.py
```

The smoke test obtains the key into process memory without printing or saving
it, checks unauthenticated rejection, scores bundled `pear` and `bear` for /p/,
and requires noisy input to return a structured retry error. It sends no retained
microphone recordings. Evidence is written to
`build/reports/azure/scorer-smoke.json`. It validates deployment behavior, not
pronunciation calibration.

Deployment verified on 2026-10-27: 53 backend tests passed locally; the deployed
endpoint returned 401 without a key, reported the model loaded with a key,
scored bundled `pear` 83.01686 and `bear` 1.67224 for /p/, and rejected generated
noise with HTTP 400 / `noisy_audio`. These are bundled examples, not the user's
retained microphone recording. Initial image build: `cx1`, tag `20261027-v1`,
digest `sha256:78bcf292a817cf89ec21683981af8837ba834f365d19fb1449fb2a759dc12067`.

## Public image audit (2026-10-27)

Published tag: `estebanlopez/minimalpairs-scorer:20261027-v1`.
Published digest:
`sha256:a75e59e2e36889f71b7fb5272b148e913ee57fffae69ded309387824e3d7b725`.
Anonymous Docker Hub access and the configuration digest were verified against
the locally audited image. Trivy reported no secrets. The full-layer audit
checked 34 layers / 58,005 files against four current deployment credentials and
found no matches or private configuration. Seven WAV files in Microsoft's base
image were verified byte-for-byte against CPython 3.12.15 public test fixtures;
their source URLs and hashes are recorded in `scripts/public_scorer_fixtures.json`.
No personal microphone recordings or application data are published.

The clean build excludes Hugging Face download caches from the final layers and
includes model attribution and the Apache 2.0 license. Audit evidence is in
`build/reports/dockerhub/layer-audit.json`, `trivy-secrets.json`, and
`public-manifest.json`. The local container also loaded the model offline.

Migration completed after the rendered Speak test passed against revision
`minimalpairs-scorer--0000001` with 100% traffic: pear /p/ 83.01685,
measure /ʒ/ 81.139786, rope /p/ 97.81593. Playback and reopened Progress
persistence passed; noise did not create a fourth result. These were bundled
PCM examples, not a fresh physical microphone session.

The ACR connection was removed and `Microsoft.ContainerRegistry/registries/
minimalpairsscorerb80386` was deleted only after that verification. The resource
group now contains the storage account, managed environment, identity, and
container app; no registry remains. This removes future charges for that ACR.
Compute and host-storage usage still incur their normal charges. The identical
storage-account name was preserved. Do not roll back to the old ACR digest;
use retained Docker Hub digests for future rollbacks.

After registry deletion, the endpoint still rejected unkeyed requests with 401,
scored pear /p/ 83.01685 and bear-as-/p/ 1.67224, and rejected noise with
`noisy_audio`. Evidence: `build/reports/dockerhub/post-deletion-smoke.json`.

## Shorter scale-to-zero cooldown

The deployment script reapplies a 60-second cooldown after image updates. To
change this setting without rebuilding or publishing an image:

```sh
build/phoneme-model-venv/bin/python scripts/configure_azure_scorer_scaling.py --cooldown 60
```

The script sets the cooldown and an explicit HTTP rule using the supported
Functions scaling override (`allowScalingRuleOverride=true`, HTTP concurrency
10). It waits for the new revision and verifies the container configuration and
0–1 replica limits remain unchanged. This backend has only HTTP triggers; adding
queue or timer functions would require revisiting these explicit rules. Use `--cooldown 300` to restore the previous setting if longer warm
sessions are preferred. Azure's polling and shutdown behavior mean the observed
scale-to-zero time is not guaranteed to equal exactly 60 seconds. Revision GET
responses currently omit cooldown/polling fields, so inspect the app GET setting
and measure actual idle shutdown rather than treating a null revision field as
proof that the default is in effect.

The `--cold-start` Speak verification retrieves the Function key into memory,
then makes management-plane replica checks until zero replicas are observed. It
does not send another health request before the rendered UI test. Speak must
score its first bundled recording within the existing client timeout, replay
recordings, persist initial/medial/final consonant scores, and reject noise.
`cold-start.json` records the replica observations and `scoring-times-ms.json`
records response times under `composeApp/build/reports/articulator-azure/`.

Speak now sets both request and socket timeouts to 120 seconds (connection timeout
30 seconds). Temporary HTTP 502/503/504 responses retry the same in-memory WAV,
with at most eight attempts and one 120-second total deadline. The service has no
score-persistence side effect; Progress saves once after the final validated
response. Authentication and audio-quality failures are not retried, and caller
cancellation remains cancellation. Cold-start recording/playback verification
uses bundled PCM, not a new physical microphone capture.

Verified on 2026-10-27 against revision `minimalpairs-scorer--0000003`: Azure's
app configuration retained `cooldownPeriod=60`, explicit HTTP rules, and 0–1
replicas. The test observed zero replicas after 247 seconds since its final
health request; this does **not** demonstrate a one-minute shutdown or a specific
bill reduction. Platform behavior and management-plane observation lag still
need to be distinguished before projecting savings from this setting.

From that confirmed zero-replica state, Speak scored pear /p/ 83.01685 in
44.999 seconds, followed by measure /ʒ/ 81.139786 in 0.421 seconds and rope /p/
97.81593 in 0.229 seconds. Original PCM playback, reopened Progress persistence,
and noise rejection passed. The earlier cold test failed with the backend
reporting a client-aborted upload; explicitly extending the socket timeout
addressed that failure in the subsequent test. The full JVM suite passed 212
tests with two opt-in skips; Android and iOS simulator compilation passed.
Evidence is retained under `build/reports/cooldown/`.
