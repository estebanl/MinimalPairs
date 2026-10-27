Feature: Articulator Lab guided speaking practice

  As a learner
  I want to choose a contrast and compare my sound with a reference
  So that I can practise without confusing estimates with measured anatomy

  Scenario: Choose a contrast and example pair
    Given the Speak screen is idle
    When I select a catalog contrast and one of its example pairs
    And I select either sound
    Then the labels and references show the selected word and phoneme
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
    Then Camera is the selected visual mode
    And the camera remains off until I choose Start camera
    And the preview area explains how to start the practice mirror
    And the selected vowel has a nearby lip reference and placement cue
    And recorded phoneme example playback is available outside sound measurement details
    And the recording action remains pinned while the content scrolls

  Scenario: Match the practice mirror visual hierarchy
    Given the Camera mode is selected
    Then the two vowel tiles share one selector card with a change-sound control
    And all four labeled icon tabs remain in one row at standard text sizes
    And larger accessibility text uses two rows without clipping labels
    And camera status and start or stop controls overlay the preview
    And the lip reference is beside the serif practice heading
    And example playback and expandable sound measurements are separate cards
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
