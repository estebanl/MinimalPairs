# **Project Specification: Modern "Ship or Sheep?" Pronunciation App (KMP)**

# **1\. Executive Summary & Vision**

A cross-platform mobile application (Android & iOS) built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**, modernizing Ann Baker's classic "Ship or Sheep?" pedagogy using modern speech AI, articulatory biofeedback, and comic dialogues.

The platform bridges phonetics theory and interactive mobile learning, enabling learners to discriminate, visualize, and produce challenging English minimal pairs through real-time audio and computer vision feedback loops.

---

# **2\. Core Modules & Pedagogical Architecture**

## **Module 1: Ear Trainer (Minimal Pair Discrimination)**

Focuses on ear-first discrimination drills to build perceptual mastery before speech production.

* **Algorithm**: Modified Spaced Repetition (SM-2) optimized for phoneme confusion matrices.  
* **Playback Engine**: Dual-speed audio pipeline supporting normal speed (1.0x) and time-stretched (0.75x) playback via phase-vocoded processing to preserve original pitch contours.  
* **Drill Types**: ABX testing, forced choice, and target sound identification within masking noise.

## **Module 2: Articulator Lab (Biofeedback & Production)**

Provides structural feedback on physical tongue, lip, and jaw placement during production.

* **Computer Vision**: Real-time facial landmark tracking via MediaPipe Face Mesh (468 points) to measure lip aperture, rounding, and jaw position.  
* **Acoustic Analysis**: Linear Predictive Coding (LPC) for formant extraction (\$F\_1\$, \$F\_2\$, \$F\_3\$) mapping acoustic output to target vowel trapezoid coordinates.  
* **Visual Engine**: Interactive 3D oral cavity render displaying target versus actual vocal tract configurations.

Implementation status (October 2026): Speak supports all catalog contrasts and
example words, isolated-vowel recording, replay, acoustic placement feedback, and
ten 3D teaching references. Four references are explicitly labeled derived poses.
The 3D tab compares target and observed formants alongside the reference anatomy;
it does not reconstruct the learner's internal vocal tract. Camera feedback
measures projected lip opening and circularity and supports a temporary personal
comparison pose. Calibrated jaw distance, internal anatomy reconstruction, and
physical verification of the 30 FPS tracking budget remain open requirements.
The camera pipeline now targets 30 analyses per second with backpressure,
timestamp-aware smoothing, and measured-rate diagnostics. Neural scoring is
an optional local-checkpoint CTC service with forced alignment and uncalibrated
confidence; the evaluated checkpoint rejects isolated-phoneme requests because
independent vowel tests found it unreliable, so Speak uses the labeled acoustic
estimate. MFA and human-calibrated GOP are not implemented. See the
[Articulator Lab behavior contract](specs/features/articulator_lab.feature) and
[scoring setup](docs/ARTICULATOR_SCORING.md).

## **Module 3: The Meaning Trap (Generative Interactive Dialogues)**

Contextualizes pronunciation errors through generative narrative consequences.

* **Comic Engine**: Multi-panel visual stories where mispronunciations trigger humorous semantic misunderstandings (e.g., requesting a "ship" instead of a "sheep").  
* **Dialogue Agent**: LLM-guided conversational branching calibrated to target specific minimal pair vulnerabilities.

## **Module 4: Rhythm & Pitch Ribbon (Prosody & Connected Speech)**

Extends focus beyond isolated sounds to sentence-level prosody and intonation.

* **Pitch Tracking**: Continuous fundamental frequency (\$F\_0\$) extraction plotted against target native reference contours.  
* **Connected Speech**: Automated detection and scoring of weak forms (\$\\partial\$), linking sounds, and stress-timed rhythm patterns.

---

# **3\. Technical Architecture (KMP)**

## **Shared Codebase Layout**

The core business logic, user interface, and state management are fully shared across targets using modern Kotlin tools.

| Layer | Technology | Primary Responsibilities |
| :---- | :---- | :---- |
| **UI Framework** | Compose Multiplatform | Cross-platform declarative UI, responsive canvas layouts |
| **Navigation / Architecture** | Decompose | Component tree management, state restoration, backstack handling |
| **Dependency Injection** | Koin | Compile-time dependency graphs across common and native modules |
| **Networking** | Ktor Client | WebSockets for real-time streaming audio, REST for static sync |
| **Persistence** | Room / SQLDelight | Local caching of audio metadata, user progress, and spaced repetition metrics |

## **Hardware Abstraction Layer (`expect`/`actual`)**

### **Audio Recording Pipeline**

* **Target Sample Rate**: PCM 16kHz, 16-bit, mono channel.  
* **Android**: Low-latency `Oboe` native wrapper over `AAudio` / `OpenSL ES` falling back to `AudioRecord`.  
* **iOS**: Native `AVAudioEngine` pipeline with custom tap nodes feeding shared C++ processing libraries.

### **Vision & Tracking Pipeline**

* **Android**: `CameraX` API coupled with `MediaPipe Face Landmarker` native solution.  
* **iOS**: `AVFoundation` video capture pipeline integrated with `Vision.framework` and `ARKit` depth maps where available.

## **Backend Microservices Architecture**

* **API Framework**: FastAPI application exposing both gRPC endpoints for audio streaming and REST endpoints for state management.  
* **Acoustic Alignment**: Montreal Forced Aligner (MFA) integrated with fine-tuned `Wav2Vec 2.0` models.  
* **Phoneme Scoring**: Implementation of Goodness of Pronunciation (GOP) algorithms calculating posterior probabilities from neural net acoustic models.

---

# **4\. Spec-Driven Development (SDD) Contract Artifacts**

## **4.1 Phonetics Target Specification (`specs/phonetics/vowel_contrasts.yaml`)**

version: "1.0"

contrast\_pairs:

  \- id: "i\_vs\_I"

    target\_phonemes: \["iː", "ɪ"\]

    description: "Close front unrounded vs. Near-close near-front unrounded"

    examples:

      \- pair: \["sheep", "ship"\]

      \- pair: \["feet", "fit"\]

      \- pair: \["seat", "sit"\]

    acoustic\_bounds:

      phoneme\_1: \# /iː/

        f1\_range\_hz: \[250, 380\]

        f2\_range\_hz: \[2100, 2600\]

        min\_duration\_ms: 120

      phoneme\_2: \# /ɪ/

        f1\_range\_hz: \[380, 520\]

        f2\_range\_hz: \[1700, 2100\]

        max\_duration\_ms: 100

    visual\_indicators:

      lip\_aperture\_ratio: 0.15

      jaw\_drop\_mm: \[5, 12\]

## **4.2 Audio Recorder Interface Contract (`specs/contracts/audio_recorder_spec.md`)**

\# Audio Recorder Technical Contract

\#\# Performance Budgets

\- \*\*Maximum System Latency\*\*: \<= 40ms from hardware input to PCM buffer availability.

\- \*\*Buffer Size\*\*: 512 frames per buffer callback.

\- \*\*Sampling Rate\*\*: 16,000 Hz fixed.

\- \*\*Bit Depth\*\*: 16-bit signed integer PCM.

\#\# State Machine

1\. \`UNINITIALIZED\`: Hardware resources released.

2\. \`READY\`: Hardware bound, buffers allocated.

3\. \`RECORDING\`: Active PCM stream pushed to audio channel.

4\. \`PAUSED\`: Hardware pipeline active, buffer output suspended.

5\. \`ERROR\`: Hardware conflict or buffer overrun state.

## **4.3 Phoneme Scorer API (`specs/api/phoneme_scorer.openapi.yaml`)**

openapi: 3.0.3

info:

  title: Phoneme Scorer Service

  version: 1.0.0

paths:

  /v1/score/phoneme:

    post:

      summary: Evaluate phoneme quality using Goodness of Pronunciation (GOP)

      requestBody:

        required: true

        content:

          multipart/form-data:

            schema:

              type: object

              properties:

                audio\_file:

                  type: string

                  format: binary

                target\_transcript:

                  type: string

                  example: "sheep"

                expected\_ipa:

                  type: string

                  example: "ʃ iː p"

      responses:

        '200':

          description: Successful evaluation

          content:

            application/json:

              schema:

                type: object

                properties:

                  overall\_score:

                    type: number

                    format: float

                    example: 88.5

                  phonemes:

                    type: array

                    items:

                      type: object

                      properties:

                        phoneme:

                          type: string

                        gop\_score:

                          type: number

                        start\_time:

                          type: number

                        end\_time:

                          type: number

## **4.4 Feature Acceptance Suite (`specs/features/ear_trainer.feature`)**

Feature: Ear Trainer Minimal Pair Discrimination

  As an ESL learner

  I want to listen to two visually similar target words

  So that I can train my auditory system to distinguish subtle phoneme variations

  Background:

    Given the user has selected the vowel contrast "/iː/ vs /ɪ/"

    And the audio playback speed is set to "1.0x"

  Scenario: Successful identification of target minimal pair sound

    Given an audio prompt plays the target word "ship"

    When the user selects option "ship" within "3.0" seconds

    Then the system records a successful discrimination trial

    And the confidence weight for phoneme "/ɪ/" in SM-2 is increased

    And visual positive reinforcement is displayed on screen

  Scenario: Slow-motion diagnostic playback trigger

    Given the user has failed the target contrast "/iː/ vs /ɪ/" "3" consecutive times

    When the next trial starts

    Then the audio playback speed automatically adjusts to "0.75x" phase-vocoded

    And a visual hint pointing to formant visualizers is rendered  
