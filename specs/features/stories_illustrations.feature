Feature: Illustrated Stories scenes
  Original vector illustrations keep each character and setting consistent across a story.

  Scenario: See a story setting before recording
    Given the learner selects any of the four original Stories
    Then an illustration shows the story's character in its setting
    And an accessible description explains the scene

  Scenario: See the consequence of the chosen pronunciation
    Given an original Story chooses the intended or alternate ending
    Then its illustration shows the corresponding narrative consequence
    And the character and setting remain recognizable from the prompt
    And its accessible description changes to explain that consequence

  Scenario: Browse Stories on phone or desktop
    Then illustrations fit the available width without cropping
    And they keep a two-to-one aspect ratio
    And their width is capped at 760 dp on larger windows

  Scenario: Add a story before its bespoke artwork is available
    Given a story has no bespoke illustration
    Then a conversation illustration is shown
    And its accessible description names the character or chosen meaning
