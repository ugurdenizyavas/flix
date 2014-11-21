Feature: Flix Delta Service
  Flix Delta Service

  Scenario: flix delta service with success
    Given Flix delta for publication SCORE locale en_GB with success
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service for publication SCORE locale en_GB should be done

  Scenario: flix delta service no upload
    Given Flix delta for publication SCORE locale en_GB with success
    When I request flix delta service no upload for publication SCORE locale en_GB
    Then Flix delta service no upload for publication SCORE locale en_GB should be done

  Scenario: flix delta service with delta error
    Given Flix delta for publication SCORE locale en_GB with delta error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error retrieving global sku delta error

  Scenario: flix delta service with delete current error
    Given Flix delta for publication SCORE locale en_GB with delete current error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error deleting current flix xmls error

  Scenario: flix delta service with update last modified date error
    Given Flix delta for publication SCORE locale en_GB with update last modified date error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error updating last modified date error

  Scenario: flix delta service with get category error
    Given Flix delta for publication SCORE locale en_GB with get category error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error getting octopus category feed error

  Scenario: flix delta service with save category error
    Given Flix delta for publication SCORE locale en_GB with save category error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error saving octopus category feed error

  Scenario: flix delta service with get ean code error
    Given Flix delta for publication SCORE locale en_GB with get ean code error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error getting ean code feed error

  Scenario: flix delta service with ops error
    Given Flix delta for publication SCORE locale en_GB with ops error
    When I request flix delta service for publication SCORE locale en_GB
    Then Flix delta service should give HTTP 500 error calling repo ops service error

  Scenario: flix delta service with invalid publication
    When I request flix delta service with invalid publication parameter
    Then Flix delta service should reject with publication parameter error

  Scenario: flix delta service with invalid locale
    When I request flix delta service with invalid locale parameter
    Then Flix delta service should reject with locale parameter error

  Scenario: flix delta service with invalid start date
    When I request flix delta service with invalid sdate parameter
    Then Flix delta service should reject with sdate parameter error

  Scenario: flix delta service with invalid end date
    When I request flix delta service with invalid edate parameter
    Then Flix delta service should reject with edate parameter error

