Feature: Catalog and listings

  Scenario: Owner publishes an article and finds it via search
    Given a browser session with auth response capture enabled
    When I register a fresh user through the UI
    And I verify the signup code
    And I upload valid identity documents
    And I have a payout IBAN on file
    And I publish a sale article through the UI
    Then the article can be found in the catalog search