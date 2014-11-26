Feature: Flix Product Service
  Flix Product Service

  Scenario: flix product service basic
    Given Repo product for product x1.ceh
    Given Xml save success for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    Given Category xperia for product x1.ceh
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh category xperia ean code 111222 should be done

  Scenario: flix product service with process id
    Given Repo product with process id 123 for product x1.ceh
    Given Xml save success with process id 123 for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    Given Category tv for product x1.ceh
    When I request flix product service with process id 123 for product x1.ceh
    Then Flix product service with process id 123 for product x1.ceh category tv ean code 111222 should be done

  Scenario: flix product service ean code not found
    Given Category tablet for product x1.ceh
    Given Repo product for product  x1.ceh
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh should fail with ean code not found

  Scenario: flix product service category not found
    Given Repo product for product  x1.ceh
    Given Category headphone for product XXX
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh should fail with category not found

  Scenario: flix product service with category param
    Given Repo product for product x1.ceh
    Given Xml save success for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    When I request flix product service with category playstation for product x1.ceh
    Then Flix product service for product x1.ceh category playstation ean code 111222 should be done

  Scenario: flix product service repo product not found
    Given Repo product not found for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    Given Category xperia for product x1.ceh
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh should fail with HTTP 404 error getting product from repo

  Scenario: flix product service xml save error
    Given Repo product for product x1.ceh
    Given Xml save error for product x1.ceh
    Given Octopus ean code 111222 for product x1.ceh
    Given Category xperia for product x1.ceh
    When I request flix product service for product x1.ceh
    Then Flix product service for product x1.ceh should fail with HTTP 500 error saving flix xml to repo
