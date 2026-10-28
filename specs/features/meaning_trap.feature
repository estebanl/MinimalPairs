Feature: Stories reply feedback and pronunciation branching
  Stories use full-phrase alignment to compare the vowel that changes the meaning.
  Sound confidence is experimental model output, not pronunciation accuracy.

  Scenario: Show recording and evaluation state
    Given the learner opens a story
    When the learner records a reply
    Then the screen shows listening guidance and a stop control
    And example audio and example endings are disabled during recording
    When the learner stops recording
    Then the screen shows a scoring indicator and checking feedback
    And recording, example audio, and example endings are disabled during scoring
    And changing the story, leaving the screen, or resetting cancels unfinished scoring

  Scenario: Choose an ending using the target vowel within the full phrase
    Given a clear voiced reply is recorded
    When both directions of the vowel contrast receive valid results from the same model
    Then the vowel at the story's explicit target phoneme index supplies each candidate's score
    And the phrase average does not choose the ending
    And only the target vowel differs between the two expected IPA sequences
    And selected-phoneme phrase scores exclude the global sequence-agreement penalty
    And the two candidate vowels share the selected CTC state for alignment
    And confidence compares the two candidates only after checking their full-vocabulary evidence
    And reversing the pair must return the same target region and evidence
    And the service permits internal phrase pauses and up to 30 seconds of recording
    When one candidate scores at least 80 out of 100 and exceeds the other by at least 15 points
    Then the corresponding ending is shown and played
    And both sound-confidence scores and their experimental meaning are visible
    And the intended-vowel score and chosen outcome are saved in Stories history
    And the differing vowel phonemes update the shared confusion tracker

  Scenario: An uncertain reply leaves the story open
    Given neither candidate meets both the score and margin thresholds
    Then no ending is chosen
    And both candidate scores and guidance to hear and record the full phrase again are shown
    And no practice result or confusion trial is recorded

  Scenario: Diffuse multilingual probabilities can still support a clear contrast
    Given the target vowel's absolute probability is below the old single-symbol confidence cutoff
    And the declared pair has sufficient full-vocabulary support and a clear conditional winner
    Then the ending uses contrast confidence rather than the old absolute probability
    And an ending still requires an 80-point contrast score and a 15-point lead

  Scenario: Pair normalization cannot invent evidence
    Given the selected region contains another supported vowel, weak evidence, or sustained unrelated speech
    Then scoring reports an uncertain alignment
    And a matching spike does not hide sustained competing speech
    And no ending or practice result is produced

  Scenario: Record a clean continuous phrase promptly
    Given a clear phrase recording contains less than 20 percent silence
    Then the phrase noise-floor estimate does not require 20 percent silence
    And noise, clipping, and no-speech checks remain active

  Scenario: Recording or scoring cannot complete
    Given microphone access fails, no clear voice is captured, or phrase scoring fails
    Then a visible error explains the problem and offers a retry
    And scoring controls return to the idle state
    And no ending is chosen and no practice result or confusion trial is recorded
    And a whole-recording acoustic fallback never chooses an ending
    And older services without selected-phoneme phrase scoring report unavailability

  Scenario: Explore example endings without altering personal feedback
    When the learner explores either example ending
    Then the ending is labelled as an example
    And no personal confidence score is fabricated
    And no practice result, confusion count, or confidence weight is changed
