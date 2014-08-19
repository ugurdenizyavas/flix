Feature: Flix
  Flix services

  Scenario: flix delta service
    Given Flix delta for publication SCORE locale en_GB
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service for publication SCORE locale en_GB should be done

  Scenario: flix delta service with invalid publication
    When I request flix delta service with invalid publication parameter
    Then Flix delta service should give publication parameter error

  Scenario: flix delta service with invalid locale
    When I request flix delta service with invalid locale parameter
    Then Flix delta service should give locale parameter error

  Scenario: flix delta service with invalid start date
    When I request flix delta service with invalid sdate parameter
    Then Flix delta service should give sdate parameter error

  Scenario: flix delta service with invalid end date
    When I request flix delta service with invalid edate parameter
    Then Flix delta service should give edate parameter error

  Scenario: flix sheet service
    Given Flix json for sheet x1.ceh
    When I request flix sheet service for process 123 sheet x1.ceh
    Then Flix sheet service for process 123 sheet x1.ceh should be done

  Scenario: flix sheet service with invalid urn
    When I request flix sheet service with invalid urn
    Then Flix sheet service should give invalid urn error

  Scenario: flix sheet service with invalid ean code
    When I request flix sheet service with invalid ean code
    Then Flix sheet service should give invalid ean code error

  Scenario: flix package service with error
    Given Repository ops service with error
    When I request flix package service for publication SCORE locale en_GB
    Then Flix package service for publication SCORE locale en_GB should get error

  Scenario: flix package service with success
    Given Repository ops service with success
    When I request flix package service for publication SCORE locale en_GB
    Then Flix package service for publication SCORE locale en_GB should be successful
