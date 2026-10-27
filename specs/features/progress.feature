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
    Then the result is staged durably when local storage permits
    And a retry action and unsaved-result message are shown
    When saving succeeds after a retry
    Then the result is saved once
    And listening mastery is not applied twice

  Scenario: Durably staged saves recover after restart
    Given a practice result was staged but could not be applied
    When the app is reopened
    Then the result is retried from the durable queue
    And a previously committed result is not applied twice
    But if staging itself fails the learner is told to keep the app open and retry

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
    When live accuracy updates reorder the contrast list
    Then the displayed contrast and its practice action remain selected
    And if that contrast disappears the first available contrast becomes selected

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
    When a detail section is collapsed and reopened
    Then its selected sound and expanded history choices are retained

  Scenario: Browse older practice results
    Given more than forty listening answers and scored speaking recordings
    When the learner browses either history
    Then older and newer pages make every saved result accessible
    And each expanded page shows at most twenty results in newest-first order
    And the visible range and total count are shown

  Scenario: Filter progress by date and contrast
    Given saved practice on multiple dates and contrasts
    When the learner selects a date range and contrast
    Then listening accuracy, trends, sound patterns, and history use matching answers
    And speaking summaries and history use matching recordings
    And date ranges use local calendar days including today
    And streaks and due reviews remain global and are labeled accordingly
    When no records match the filters
    Then a filtered empty state offers a way to clear the filters
    When the filters change
    Then history browsing starts from the newest matching results

  Scenario: History updates avoid reloading unaffected records
    Given a loaded progress snapshot
    When a new practice result is saved
    Then changed database rows are read consistently with their review updates
    And unaffected history lists are reused
    And listening counts and sound patterns are updated from changed answers

  Scenario: Portable backup and reset
    When the learner exports progress
    Then all saved results and review schedules are written to a versioned JSON file
    And filters do not restrict the backup
    And audio and unsaved results are excluded
    When the learner confirms importing a valid backup
    Then identical records are merged once and conflicting records reject the entire import
    When the learner cancels resetting progress
    Then no data changes
    When the learner confirms resetting progress
    Then saved results, schedules, and pending saves are removed atomically
    And exercises and settings remain available

  Scenario: Stories and Rhythm have separate progress
    Given a real voiced recording completes with a valid evaluation in Stories or Rhythm
    Then its score and target are saved in that module's history
    And listening accuracy, streaks, reviews, and speaking summaries are unchanged
    And demonstrations, cancelled evaluations, silence, and invalid scores are excluded
    When the learner chooses a module practice action in Progress
    Then the corresponding story or rhythm pattern opens
