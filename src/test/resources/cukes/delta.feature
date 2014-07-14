Feature: Flix
  Flix service

  Scenario: Flix flow
    When I request flix media generation for publication SCORE locale en_GB
    Then Flix media generation for publication SCORE locale en_GB should be started

  Scenario: Flix sheet flow
    When I request flix sheet import for process 123 sheet x1.ceh
    Then Flix sheet import for process 123 sheet x1.ceh should be started
