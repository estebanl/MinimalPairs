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

  Scenario: Adaptive drill progression from Forced Choice to ABX
    Given the user achieves a mastery score above "0.7" on contrast "/iː/ vs /ɪ/"
    When the next training set is generated
    Then the drill type progresses to "ABX" testing
