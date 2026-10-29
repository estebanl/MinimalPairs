Feature: Stories reply feedback and pronunciation branching
  Stories use full-phrase alignment to compare the vowel that changes the meaning.
  Sound confidence is experimental model output, not pronunciation accuracy.

  Scenario: Continue a pronunciation-dependent conversation
    Given a reply has resolved to an intended or alternate ending
    When the learner chooses Continue story
    Then the next prompt continues from the selected consequence and preceding conversation
    And every available story supports three turns and all preceding outcome paths
    And follow-up dialogue is AI-authored and bundled with the app without a runtime generation service
    And the target phrase, phrase IPA, vowel index, alternate vowel, and teaching takeaway stay unchanged
    And each real recorded turn uses the same strict two-direction phrase scorer and ending thresholds
    And conversation history shows previous prompts, matched meanings, and consequences
    And the third resolved turn completes the conversation and offers restart

  Scenario: Reject invalid follow-up content and recover from loading failure
    Given the next turn cannot load or its target metadata or narrative is invalid
    Then the current turn, completed recording, scores, and progress remain available
    And Retry next turn retries the same branch path without duplicating history
    And Restart story returns to the bundled opening prompt
    And changing stories, resetting, leaving, or destroying the screen cancels pending continuation
    And cancelled or late work cannot overwrite another story

  Scenario: Explore example conversations without creating personal progress
    Given the learner continues from a simulated ending
    Then follow-up turns are labelled Example conversation with no personal scores
    And example endings select conversational branches without calling the scorer
    And recording is disabled until the conversation is restarted
    And no personal result, confusion trial, or recommendation evidence is produced
    And selecting an example during a follow-up also switches to example mode

  Scenario: Preserve reply audio during conversations
    Given a follow-up uses new dialogue with the same target phrase
    Then Hear phrase plays the unchanged reference reply
    And original prompt and ending recordings are not played for the new dialogue
    And a completed real reply remains replayable until successful continuation or reset
    And replay does not change conversation history or personal scores

  Scenario: Recommend practice from saved sound confusions
    Given the learner has saved listening answers or scored Stories replies
    Then an available story is recommended for a contrast with observed mistakes
    And its explanation identifies the contrasting sounds and saved attempt counts
    And choosing the recommendation opens that story
    And manual story selection remains available
    And Progress date and contrast filters do not change the recommendation evidence
    And example endings do not change recommendation evidence
    And reopening restores the saved evidence and resetting progress clears it

  Scenario: Offer a neutral starting story without mistakes
    Given there are no saved mistakes for the available story contrasts
    Then a starting story is suggested without claiming the learner has a weak sound

  Scenario: Practise every catalog vowel contrast in context
    Given the learner browses the available stories
    Then every catalog vowel contrast has a selectable story
    And each story provides a full target phrase, explicit vowel index, two endings, and a teaching takeaway
    And prompt, phrase, and both ending reference recordings are bundled

  Scenario: Recover from reference audio failures
    Given prompt, phrase, or ending audio cannot load or play
    Then a visible playback error explains how to retry
    And the relevant playback control becomes available again
    And existing reply feedback, scores, and practice records are preserved
    And cancelling playback or leaving the story does not show a playback failure

  Scenario: Replay the latest recorded reply
    Given the learner has stopped recording a nonempty reply
    When scoring succeeds, remains uncertain, or fails
    Then Replay your reply plays the captured audio unchanged
    And replay does not evaluate the recording again or change scores, progress, or confusion counts
    And replay is disabled during recording, scoring, or any audio playback
    And starting a recording stops any pending or active playback before microphone capture

  Scenario: Keep recorded replies temporary
    Given a completed reply is available for replay
    When the learner starts another recording, changes stories, or resets the story
    Then the previous reply is discarded and cannot be replayed
    And only a newly completed nonempty recording makes replay available again
    And recordings are held in memory only and are not added to progress history or backups
    And example endings never create or replace a recorded reply

  Scenario: Recover from reply replay failure
    Given a completed reply is available
    When replay fails
    Then the screen offers guidance to retry Replay your reply
    And the captured reply and existing scoring feedback remain available

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
    Then the corresponding ending is shown
    And opening-scene endings play their matching bundled recording
    And both sound-confidence scores and their experimental meaning are visible
    And the intended-vowel score and chosen outcome are saved in Stories history
    And the differing vowel phonemes update the shared confusion tracker

  Scenario: An uncertain reply leaves the story open
    Given neither candidate meets both the score and margin thresholds
    Then no ending is chosen
    And both candidate scores and guidance to hear and record the full phrase again are shown
    And no practice result or confusion trial is recorded

  Scenario: Score established British LOT and THOUGHT realizations
    Given the declared phrase contrast is /ɒ/ versus /ɔː/
    Then /ɒ/ can receive evidence from distinct model tokens [ɒ] or [ɔ]
    And /ɔː/ can receive evidence from distinct model tokens [ɔː] or [oː]
    And the two pronunciation groups are disjoint and use the same alignment in either direction
    And public IPA labels, absolute evidence checks, and ending thresholds stay unchanged
    And other contrasts and word scoring do not apply these groups as global aliases

  Scenario: A matching vowel in unrelated speech cannot establish the prompted phrase
    Given the recording contains a well-supported candidate vowel
    But a majority of surrounding phrase phonemes lack 10 out of 100 local posterior support
    Then scoring reports an uncertain alignment and asks for the full phrase again
    And the phrase context gate never changes the selected vowel's score
    And no ending or practice result is produced

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
