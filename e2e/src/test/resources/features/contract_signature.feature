Feature: Contract and advanced e-signature

  Scenario: Both parties sign a rental contract and the signed PDF is available
    Given a browser session with auth and contract response capture enabled
    And Alice registers, verifies and completes KYC
    And Alice publishes a rental article
    And Bob registers, verifies and completes KYC
    When Bob starts the rental contract for the article
    And Bob signs it using the OTP sent to his email
    Then the contract status is PENDING_SIGNATURES
    And Bob completes the deposit payment
    Then the contract status is AWAITING_COUNTERPARTY
    When Alice signs it using the OTP sent to her email
    Then the contract status is ACTIVE
    And a signed PDF is available for download