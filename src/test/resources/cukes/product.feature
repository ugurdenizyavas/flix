Feature: Flix Product Service
  Flix Product Service

  Scenario: flix product service with process id
    Given Flix json for product x1.ceh process id 123
    Given Octopus ean code 111222 for product x1.ceh
    When I request flix product service for product x1.ceh process id 123
    Then Flix product service with process id 123 for product x1.ceh ean code 111222 should be done

  Scenario: flix product service no process id
    Given Flix json for product x1.ceh no process id
    Given Octopus ean code 111222 for product x1.ceh
    When I request flix product service for product x1.ceh no process id
    Then Flix product service with no process id for product x1.ceh ean code 111222 should be done

  Scenario: flix product service no ean code
    Given Flix json for product x1.ceh no process id
    When I request flix product service for product x1.ceh no process id
    Then Flix product service for product x1.ceh should fail

