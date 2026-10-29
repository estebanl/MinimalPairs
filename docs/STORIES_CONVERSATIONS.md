# Stories conversations

Stories supports three-turn conversations across all seven contrasts. Following
the user's direction to generate the stories ourselves, the follow-up text is
authored here and bundled with the application. There is no runtime LLM call,
credential requirement, or backend deployment for this feature. Voice scoring
continues to use the configured phoneme service.

The opening scenes remain in `MeaningTrapCatalog`. The 42 follow-up scenes in
`composeApp/src/commonMain/composeResources/files/stories/dialogue.json` cover both
second-turn paths and all four third-turn paths for each story. Content provenance
and a version are included in the document. A path such as `alternate/intended`
means that the opening produced a misunderstanding and the second reply produced
the intended meaning; its third scene continues from those consequences.

## Learning and scoring contract

Each story's document contains its published phrase, space-separated IPA, selected
phoneme index, alternate vowel, minimal-pair words, and catalog identity. Loading
validates these values against the catalog and checks that every branch exists,
the history matches its preceding prompts and consequences, and the two outcomes
refer to their respective words. Invalid content is rejected before it can become
a recording target.

The learner repeats the same validated phrase in changing situations. Narratives
do not supply new IPA, target indices, acoustic evidence, scores, or thresholds.
Every real recording still uses `MeaningTrapEvaluator`: both contrast directions
must share a model, target region, and sufficient full-vocabulary evidence. An
ending still requires confidence of at least 80/100 with a lead of at least 15.
The backend's surrounding-context gate and pot/port realization groups are
unchanged. Uncertain, invalid, and failed replies never advance automatically or
produce personal results. Confidence remains experimental.

## Learner flow and recovery

After a resolved ending, **Continue story** loads the next scene. **Conversation so
far** shows prior prompts, matched meanings, and consequences; it is not a speech
transcript. The third resolved reply completes the conversation. Restart returns
to the original opening; selecting another story starts that story from turn one.
Conversation history and completed recording audio are held in memory only.

A failed load keeps the current scene, feedback, completed recording, scores, and
progress. **Retry next turn** retries the same path. **Restart story** provides a
way back to the opening, even if the follow-up document is unavailable. Pending
loading is cancelled on story change, reset, navigation, lifecycle stop, and
destruction; late results cannot replace the active scene.

Following a simulated ending starts an **Example conversation**. Example turns
select only narrative branches; recording is disabled, and neither scores nor
confusion evidence are created. Choosing an example in an existing follow-up also
enters example mode. Restart returns to personal recording practice. Earlier real
practice records, if any, remain unchanged.

New dialogue is text only. The original opening/ending recordings are not offered
as audio for different text. **Hear phrase** keeps the original validated reply
audio. Real replies retain **Replay your reply**, including scoring failures;
successful continuation clears that turn's recording.

## Verification

- `StoryDialogueResourcesTest` loads the real bundled document, visits every path,
  verifies target identity, and rejects incomplete or corrupted content.
- `StoryConversationTest` checks pronunciation-dependent history, loading retry,
  cancellation, replay, unchanged progress, scoring rejections, and example mode.
- `MeaningTrapScreenTest` renders continuation, retry, history, and completion
  controls and checks disabled states for examples and pending work.
- The existing Stories scoring and reply replay regressions remain applicable to
  every follow-up because the target metadata is unchanged.
- `LiveStoriesScoringTest` also verifies three real turns for every story against
  the configured service, replay on every turn, rejected unrelated follow-up
  speech, and 21 simulated turns without personal results.

Run `./gradlew :composeApp:jvmTest :composeApp:testAndroidHostTest
:composeApp:compileAndroidMain :composeApp:compileKotlinIosSimulatorArm64 --offline
--console=plain` for local verification. For opt-in live scorer checks, use
`build/phoneme-model-venv/bin/python scripts/azure_speak.py verify --module stories
--expected-backend-version 2.3.1` with the existing Azure configuration.
