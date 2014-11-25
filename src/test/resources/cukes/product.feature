Feature: Flix Product Service
  Flix Product Service

  Scenario: flix product service basic
    Given Flix json for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    Given Category xperia for product x1.ceh
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh category xperia ean code 111222 should be done

  Scenario: flix product service with process id
    Given Flix json with process id 123 for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    Given Category tv for product x1.ceh
    When I request flix product service with process id 123 for product x1.ceh
    Then Flix product service with process id 123 for product x1.ceh category tv ean code 111222 should be done

  Scenario: flix product service ean code not found
    Given Category tablet for product x1.ceh
    Given Flix json for product x1.ceh
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh should fail with ean code not found

  Scenario: flix product service category not found
    Given Flix json for product x1.ceh
    Given Category headphone for product XXX
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh should fail with category not found

  Scenario: flix product service with category param
    Given Flix json for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    When I request flix product service with category playstation for product x1.ceh
    Then Flix product service for product x1.ceh category playstation ean code 111222 should be done
