Feature: Ear Trainer Minimal Pair Discrimination

  As an ESL learner
  I want to listen to two visually similar target words
  So that I can train my auditory system to distinguish subtle phoneme variations

  Background:
    Given the user has selected the vowel contrast "/iː/ vs /ɪ/"
    And the audio playback speed is set to "1.0x"

  Scenario: Successful identification of target minimal pair sound
    Given the user taps "Play word" and the target word "ship" finishes playing
    When the user selects option "ship" within "3.0" seconds of playback completing
    Then the system records a successful discrimination trial
    And the confidence weight for phoneme "/ɪ/" in SM-2 is increased
    And visual positive reinforcement is displayed on screen

  Scenario: Listening begins only when requested
    Given a new question is displayed
    Then no audio plays automatically
    And both word choices are disabled
    When the user taps "Play word"
    Then word choices remain disabled until playback completes
    And the user can replay the word without resetting response timing

  Scenario: Practice uses complete spoken words
    When the user taps "Play word"
    Then the complete target word recording is played, including its consonants
    And slow playback processes the complete word recording

  Scenario: Practice audio stays clear as mastery increases
    Given the user has correctly answered questions and achieved high mastery
    When the user plays or replays a word at either playback speed
    Then no masking noise is added to prompts or word comparisons
    And subsequent questions continue to use clear word recordings

  Scenario: Word audio is unavailable
    Given the target word recording is missing or empty
    When the user taps "Play word"
    Then a retry error is displayed and word choices remain disabled
    And no isolated vowel is substituted for the word

  Scenario: Continue after reviewing an answer
    Given the user has answered a question
    Then the correct word and the user's choice are identified with text and icons
    And the answer cannot be submitted again
    When the user taps "Next word"
    Then the question returns to the top with a "Play word" control
    And playback and answer selection reset even if the same word is chosen again

  Scenario: Technical guidance is optional
    When the user opens "Sound tips"
    Then plain-language listening guidance is displayed
    And phonetic notation, mastery, and diagrams appear only after opening "More details"

  Scenario: Compare both words after answering
    Given the user has not answered the current question
    Then neither word option offers audio playback
    And comparison playback requests are ignored even after the prompt has played
    When the user submits an answer
    Then each word option displays its own play button
    And either complete word can be replayed at the selected speed without masking noise
    And only one recording can play at a time
    And comparison playback does not change the answer or score
    When the user taps "Next word"
    Then both comparison buttons are hidden again

  Scenario: Slow-motion diagnostic playback trigger
    Given the user has failed the target contrast "/iː/ vs /ɪ/" "3" consecutive times
    When the next trial starts
    Then the audio playback speed automatically adjusts to "0.75x" phase-vocoded
    And a short explanation is displayed beside the playback speed controls
    And formant visualizers remain available under "Sound tips" and "More details"

  Scenario: Adaptive drill progression from Forced Choice to ABX
    Given the user achieves a mastery score above "0.7" on contrast "/iː/ vs /ɪ/"
    When the next training set is generated
    Then the drill type progresses to "ABX" testing
