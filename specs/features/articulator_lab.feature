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

  Scenario: Use the optional offline iOS sheep and ship prototype
    Given Mouth position is showing sheep /iː/ or ship /ɪ/ on iOS
    When I choose the 3D prototype
    Then native view and opacity controls show an explicitly unreviewed reference pose
    And all required embedded textures decode before the viewer signals ready
    And only in-memory blob fetches are permitted while HTTP and HTTPS remain blocked
    And missing textures, viewer errors, or VoiceOver use the illustration fallback
    And the existing VocalTractLab 3D model and Camera tabs remain independently available

  Scenario: Reference audio shares the practice lifecycle
    Given reference audio is loading or playing
    Then target selection and recording are locked until playback finishes or stops
    When the practice component stops or is destroyed
    Then pending reference loading and playback are cancelled
    And a late result cannot start playback or save practice progress
