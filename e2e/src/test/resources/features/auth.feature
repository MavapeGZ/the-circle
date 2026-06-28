Feature: Account onboarding and sign-in

  Scenario: A new user registers, verifies email, completes KYC, and signs in again
    Given a browser session with auth response capture enabled
    When I register a fresh user through the UI
    And I verify the signup code
    And I upload valid identity documents
    Then the registration flow finishes successfully
    Given the user is logged out
    When I sign in with the same account
    And I confirm the login code
    Then the home page is shown