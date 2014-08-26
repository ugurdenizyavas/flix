Feature: Flix Sheet Service
  Flix Sheet Service

  Scenario: flix sheet service with success
    Given Flix json for sheet x1.ceh
    When I request flix sheet service for process 123 sheet x1.ceh
    Then Flix sheet service for process 123 sheet x1.ceh should be done

  Scenario: flix sheet service with invalid urn
    When I request flix sheet service with invalid urn
    Then Flix sheet service should reject with urn parameter error

  Scenario: flix sheet service with invalid ean code
    When I request flix sheet service with invalid ean code
    Then Flix sheet service should reject with ean code parameter error

