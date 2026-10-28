Feature: Ear Trainer Minimal Pair Discrimination

  As an ESL learner
  I want to listen to two visually similar target words
  So that I can train my auditory system to distinguish subtle phoneme variations

  Background:
    Given the user has selected the vowel contrast "/iː/ vs /ɪ/"
    And the audio playback speed is set to "1.0x"
    And "Auto change" is off for the new exercise

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

  Scenario: Automatically play and continue after either answer result
    Given a question is unanswered
    When the user enables "Auto change"
    Then the current prompt plays immediately unless audio is already playing
    And answer choices remain disabled until playback completes
    When the user submits a correct or incorrect answer
    Then feedback remains visible for 1.5 seconds
    And a thin countdown ring around the arrow inside "Next word" fills clockwise during the delay
    And "Continues automatically" is displayed below the button
    And exactly one progress record is created for that answer
    And the next trial starts and plays its prompt automatically
    And the question scrolls to the top even if the same word repeats
    And focus returns to the play control when playback finishes

  Scenario: Compact Auto control shares the playback button
    Given listening practice is displayed
    Then the playback button has a separate mode segment on its right with a repeat icon
    And the segment reads "Manual" when Auto change is off and "Auto" when it is on
    And there is no separate Auto change settings row or helper paragraph
    And the Auto segment exposes its full "Auto change" label and on/off state to assistive technology
    And both playback and Auto have independent keyboard and touch targets
    When the prompt is playing
    Then playback is disabled and Auto remains available
    And turning Auto off lets the current audio finish

  Scenario: Manual controls remain available during Auto change
    Given "Auto change" is on and an answer is displayed
    When the user taps "Next word" before the delay ends
    Then the next trial starts and plays its prompt automatically
    And the previous timer cannot skip another trial
    When the user turns off "Auto change"
    Then pending automatic advancement is cancelled
    And any audio already playing finishes normally
    And opening a new exercise starts with "Auto change" off

  Scenario: Replay and tips pause automatic advancement
    Given "Auto change" is on and an answer is displayed
    When the user replays the prompt, compares either word, or opens "Sound tips"
    Then automatic advancement pauses
    And the next-word arrow ring resets and the caption displays "Auto change paused"
    And the full 1.5-second delay restarts after playback finishes and tips are closed
    And the arrow ring fills again from the beginning

  Scenario: Automatic practice pauses outside the foreground
    Given "Auto change" is on
    When the exercise leaves the foreground
    Then playback stops and pending advancement is cancelled
    When the user returns to the same exercise
    Then "Auto change" remains on
    And an answered trial receives a fresh 1.5-second delay
    And an unanswered prompt that never finished plays automatically unless playback previously failed
    When the user leaves the exercise with Back
    Then no further automatic playback or advancement occurs

  Scenario: Automatic prompt failure requires an explicit retry
    Given "Auto change" is on and the new prompt cannot be played
    Then the existing retry error is displayed and answer choices remain disabled
    And the system does not advance or repeatedly retry
    And closing tips or returning to the foreground does not retry the failed prompt
    When the user explicitly retries and playback completes
    Then answering resumes the automatic practice flow

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
