Feature: Compact Listen Library

  As an ESL learner
  I want a compact list of listening contrasts
  So that I can quickly choose a sound to practice

  Scenario: Browse the listening library
    Given listening contrasts are available
    When the user opens Listen
    Then the header shows "Listen" and the total contrast count
    And all contrasts appear in one scrollable list in catalog order
    And each row shows its number, example words, IPA, and word pair count
    And the first row's number has a yellow badge before any contrast has been opened
    When the user selects a row
    Then listening practice opens for that contrast

  Scenario: Highlight the most recently opened contrast
    When the user opens a listening contrast
    And returns to Listen
    Then that contrast's number has the yellow badge
    And all other contrast numbers are unhighlighted
    And the highlighted contrast is saved with the screen's UI state
    When the user opens a different contrast
    Then the yellow badge moves to that contrast

  Scenario: Scroll through the complete library
    Given the listening contrasts exceed the screen height
    When the user scrolls to the bottom of Listen
    Then the last contrast is available to practice
    And no expand or collapse control is displayed
    And the scroll position is saved with the screen's UI state

  Scenario: Small and empty catalogs
    Given a small listening catalog is available
    When the user opens Listen
    Then all available contrasts appear without an expansion control
    And the header count reflects the available catalog
    When no listening contrasts are available
    Then "Practice sounds will appear here when available." is displayed

  Scenario: Accessible compact rows
    Given a narrow screen or large text size
    When the user browses Listen
    Then row text wraps without truncation
    And every row remains at least 56 dp tall
    And rows support keyboard activation
