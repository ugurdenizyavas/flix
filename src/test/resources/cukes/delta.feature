Feature: Flix
  Flix service

  Scenario: Flix flow
    When I request flix media generation for publication SCORE locale en_GB
    Then Flix media generation for publication SCORE locale en_GB should be started

