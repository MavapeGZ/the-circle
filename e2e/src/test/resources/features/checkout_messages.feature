Feature: Checkout and messaging

  Scenario: Receiver pays into escrow and owner signs to release
    Given an active contract between Alice owner and Bob receiver
    When Bob pays with a valid test card
    Then the payment status is ESCROWED
    And the contract status is AWAITING_COUNTERPARTY
    When Alice signs within the escrow window
    Then the payment status is RELEASED
    And a payment receipt is available

  Scenario: Buyer and seller exchange messages
    Given a contract between Alice and Bob
    When Bob sends a message "Is it still available?"
    Then Alice sees the message in her conversation with Bob
