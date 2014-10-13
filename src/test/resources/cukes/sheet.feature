Feature: Flix Sheet Service
  Flix Sheet Service

  Scenario: flix sheet service with ean code
    Given Flix json for sheet x1.ceh with process id 123
    When I request flix sheet service for process 123 sheet x1.ceh ean code 121314
    Then Flix sheet service for process 123 sheet x1.ceh ean code 121314 should be done

  Scenario: flix sheet service no ean code
    Given Flix json for sheet x1.ceh with no process id
    Given Octopus ean code 111222 for sheet x1.ceh
    When I request flix sheet service for process 123 sheet x1.ceh no ean code
    Then Flix sheet service for process 123 sheet x1.ceh ean code 111222 should be done

