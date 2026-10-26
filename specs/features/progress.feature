Feature: Persistent listening and speaking progress

  As a learner
  I want trustworthy practice results and useful next steps
  So that I can choose what to practice next

  Scenario: Listening results survive reopening
    Given the learner completes a listening answer
    When the answer and mastery update are saved
    And the app is reopened
    Then Progress shows the saved answer and mastery
    And accuracy is the percentage of correct listening answers
    And mastery is labeled as a separate estimate

  Scenario: Progress updates are consistent
    When listening answers are saved while Progress is open
    Then answer counts and mastery are read from the same database snapshot
    And summary calculation runs off the UI thread

  Scenario: Failed saves can be retried
    Given a practice result cannot be saved
    Then the result remains queued while the app stays open
    And a retry action and unsaved-result message are shown
    When saving succeeds after a retry
    Then the result is saved once
    And listening mastery is not applied twice

  Scenario: Failed reads preserve visible progress
    Given Progress has successfully loaded saved totals
    When a subsequent read fails
    Then those totals remain visible with an outdated-data message
    When the learner retries loading successfully
    Then current totals replace the outdated totals
    And live updates resume

  Scenario: Unavailable speaking history is not reported as empty
    Given the initial progress read fails
    Then Progress offers a loading retry without claiming zero scored recordings
    When the read succeeds with no speaking records
    Then the empty speaking summary is shown
    When a refresh fails after speaking records have loaded
    Then the cached speaking records and sound summaries remain visible with the outdated-data message

  Scenario: Listening activity uses local calendar dates
    Given listening answers on consecutive local dates ending yesterday
    Then the current streak remains active today
    When today ends without a listening answer
    Then the current streak resets
    And the longest historical streak remains available
    And daylight-saving changes do not split or merge local dates

  Scenario: Accuracy comparisons require comparable sample sizes
    Given at least forty listening answers
    Then recent accuracy compares the latest twenty with the preceding twenty
    And the sample counts are visible
    And smaller histories do not claim an improvement or decline

  Scenario: Sound patterns distinguish untested sounds
    Given practice on a contrast
    When the learner opens its confusion matrix
    Then rows identify played sounds and columns identify chosen sounds
    And cells show counts and percentages within each row
    And an untested row shows no data rather than zero accuracy
    And counts from other contrasts are excluded

  Scenario: Due reviews are actionable
    Given saved review schedules
    Then due contrasts are ordered oldest first
    And review buttons open the corresponding exercises
    And near-term countdowns use minutes or hours
    When time passes or a new answer changes a schedule
    Then the review queue and timing labels refresh

  Scenario: Speaking results stay separate from listening
    Given a completed recording receives a valid pronunciation score
    Then its target and score are saved in speaking history
    And listening accuracy, streaks, and reviews are unchanged
    And silence, cancelled scoring, and generated examples are not saved
    And upgrading the database preserves earlier listening data

  Scenario: Start speaking from an empty history
    Given Progress has loaded without any speaking records
    When the learner expands speaking progress and chooses Start speaking
    Then Speak opens without requiring a previously practiced sound

  Scenario: Speaking comparisons use the same target
    Given at least ten scored recordings of one sound within one contrast
    Then its latest five scores are compared with its preceding five scores
    And other sounds and contrasts are excluded from that comparison
    And speaking summaries are calculated off the UI thread before the section opens
    When the learner chooses to practice that sound
    Then Speak opens with that exact phoneme selected
    Including when the requested phoneme is the second sound in the pair

  Scenario: Detailed progress is optional
    Given saved practice results
    Then the overview shows the current streak, reviews, and three lowest-accuracy contrasts
    And the full contrast list can be expanded
    And daily activity, trends, sound patterns, and history expand independently
