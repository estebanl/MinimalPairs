Feature: Articulator Lab guided speaking practice

  As a learner
  I want to choose a contrast and compare my sound with a reference
  So that I can practise without confusing estimates with measured anatomy

  Scenario: Choose a sound pair and vowel
    Given the Speak screen is idle
    When I select a catalog vowel sound pair
    And I select either sound
    Then the labels and references show the default reference word and selected phoneme
    And there is no Example words selector
    And the playback button says Hear followed by the selected vowel
    And the screen asks me to say the vowel on its own
    And recording scores and saves that word, phoneme, and contrast
    And an old recording and score are cleared when the selection changes

  Scenario: Preserve a target during an operation
    Given recording, scoring, or playback is in progress
    Then contrast, word pair, and target selection cannot change

  Scenario: Reopen targeted practice from Progress
    Given I have changed the contrast within Speak
    When Progress opens speaking practice for another contrast and phoneme
    Then Speak selects the requested contrast and phoneme after cleanup

  Scenario: Keep failed progress saves visible
    Given a speaking result has a retryable storage error
    When I change the practice target or start another recording
    Then the save error and retry action remain available

  Scenario: Distinguish acoustic estimates from anatomy
    Given the 3D reference is visible
    When a valid acoustic measurement is available
    Then the comparison identifies the measured formants and target ranges
    And any inferred placement is labeled as an estimate
    And reference anatomy is not presented as a scan of my vocal tract
    When live input becomes silent or invalid
    Then the previous live estimate is cleared

  Scenario: Explain camera measurement limits
    Given the camera is tracking valid lip contours
    Then lip opening and contour circularity are described as image measurements
    And unavailable jaw distance or tongue depth is not replaced with invented measurements
    And a personal comparison reference is not described as a verified pronunciation target
    When face tracking is lost or the session stops
    Then live measurements are cleared

  Scenario: Report scoring provenance
    When a recording receives a score
    Then the feedback identifies whether scoring used an acoustic estimate or a model
    And a failed model service does not silently claim model-based scoring

  Scenario: Validate camera behavior on physical devices
    Given a physical Android or iOS device is available
    Then permissions, orientation, tracking loss, backgrounding, tab changes, and concurrent audio capture are checked
    And the validation report distinguishes observed passes from checks that could not run

  Scenario: Open camera practice without starting capture
    Given I open the Speak screen
    When I select Camera
    Then Camera is the selected visual mode
    And the camera remains off until I choose Start camera
    And the preview area explains how to start the practice mirror
    And the selected vowel has a nearby lip reference and placement cue
    And recorded phoneme example playback is available outside sound measurement details
    And example playback and recording remain in the pinned bottom bar while the content scrolls

  Scenario: Match the practice mirror visual hierarchy
    Given the Camera mode is selected
    Then the two vowel tiles share one selector card with a change-sound control
    And all four labeled icon tabs remain in one row at standard text sizes
    And larger accessibility text uses two rows without clipping labels
    And camera status and start or stop controls overlay the preview
    And the lip reference is beside the serif practice heading
    And the pinned bottom bar presents equally wide actions in one row
    And bottom actions allow their labels to wrap on narrow screens
    And recording credits and expandable sound measurements remain in the scrollable content
    And detailed camera measurements and explanations stay inside sound measurements
    And the dotted lip alignment guide is identified as a guide rather than a measurement

  Scenario: Keep the custom header below the device status bar
    Given the device has a top or horizontal safe-area inset
    When I open the Speak screen
    Then the title and back control remain inside the safe area
    And insets already consumed by the host are not applied twice

  Scenario: Listen to isolated recorded vowels offline
    Given any vowel contrast is selected in the articulator lab
    When the learner plays either target's example
    Then the matching bundled ElevenLabs vowel example plays without a network request
    And no synthetic formant tone is substituted if the recording is unavailable
    And an unavailable recording shows a recoverable error
    And example playback does not replace the learner's score or saved recording
    And ElevenLabs generation credits are accessible

  Scenario: Sound map explains measurements and highlights the practice target
    Given a sound pair and a selected practice vowel
    When I open Sound map
    Then the selected vowel region has a stronger fill and border and a Target label
    And the legend identifies the green dot as Your sound
    And the F1 axis explains low at top and high at bottom
    And the F2 axis identifies front and back

  Scenario: Unmeasurable audio never displays placeholder formants
    Given microphone input with energy but no usable pair of formant peaks
    When the input is analyzed
    Then the live sound position is cleared
    And a recording containing only that input is rejected without scoring

  Scenario: Replay pronunciation from the pinned bottom controls
    Given no pronunciation has been recorded
    Then the primary action is Record and playback is hidden
    When I record my pronunciation
    Then the primary action is Stop while recording
    When I stop recording and processing finishes
    Then Play mine is the primary action if audio is available
    And Example, Play mine, and Record appear in one row with icons above their short labels
    And playback stays visible while the reference content scrolls
    And the actions are disabled during processing or playback
    When I record again or select a different practice target
    Then Play mine is hidden until a recording is available outside recording mode

  Scenario: Practise the curriculum's cat and cut vowel contrast
    Given the Speak screen is idle
    When I choose cat / cut from the sound pair menu
    Then I can practise /æ/ and /ʌ/ with their matching audio and mouth references
    And acoustic guidance uses the existing bounds for each vowel
    And the catalog includes cat / cut, hat / hut, match / much, and ankle / uncle
    And Listen uses the same contrast without duplicating it

  Scenario: Speak offers every sound pair available in Listen
    Given the Speak screen is idle
    When I open the sound pair menu
    Then all 20 Listen contrasts appear in the same order with the same reference words
    And either target can be selected for every pair

  Scenario: Practise consonants in words
    Given a consonant contrast is selected
    Then each target has its own placement cue and the pair has contrast guidance
    And Example plays the selected bundled word used in Listen
    And the instruction asks me to say the word and focus on the selected consonant
    And Mouth position and Camera are available
    And vowel sound maps, vowel anatomy models, and formant targets are hidden
    When I record and stop
    Then I can replay my recording without passing a voiced-vowel detection gate
    And the phoneme service aligns the full word transcript and scores the selected consonant occurrence
    And the score is labeled experimental selected-phoneme confidence rather than word similarity
    And errors on other phonemes are not averaged into the selected score
    And the score, selected word, consonant, contrast, and recording duration are saved to Progress
    And Progress can reopen the selected consonant practice target
    And silence, unavailable phoneme service, and invalid scores show a retryable error without saving a score
    When I switch sound pairs
    Then previous audio and feedback are cleared
