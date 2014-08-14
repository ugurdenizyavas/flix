Feature: Flix
  Flix service

  Scenario: Flix flow
    Given Flix delta for publication SCORE locale en_GB
    When I request flix media generation for publication SCORE locale en_GB
    Then Flix media generation for publication SCORE locale en_GB should be done

  Scenario: Flix sheet flow
    Given Flix json for sheet x1.ceh
    When I request flix sheet import for process 123 sheet x1.ceh
    Then Flix sheet import for process 123 sheet x1.ceh should be done
