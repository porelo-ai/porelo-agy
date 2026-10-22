@credential @slow
Feature: The agy agent runs against the live provider without holding the credential

  # Holds only for an API key; a login credential is collected in a throwaway login container before reaching the vault.
  Scenario: a task nobody attaches to answers through the broker, and the credential is nowhere in it
    Given the suite runs as an unprivileged user
    And the environment variable "GEMINI_API_KEY" is set
    And the environment variable "PORELO_E2E_MODEL" is set
    Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
    And the vault holds the value of "GEMINI_API_KEY" as "google" of kind "api-key"
    And a project called "broker" of class "guarded" with a file in it
    When a task is started in "broker" for the "agy" agent and left running
    And the task's container runs:
      """
      timeout 240 agy --model "${PORELO_E2E_MODEL}" --output-format json --dangerously-skip-permissions -p "Reply with the single word PORELOLIVE and nothing else."
      """
    Then its output contains "PORELOLIVE"
    And the task's container environment does not contain the value of "GEMINI_API_KEY"
    When the task's container runs:
      """
      find /home/agent /tmp /workspace -type f ! -name agy -size -2M -exec cat {} + 2>/dev/null || true
      """
    Then its output contains "\"modelProvider\": \"gemini\""
    And its output does not contain the value of "GEMINI_API_KEY"
    And no log the task left contains the value of "GEMINI_API_KEY"
    And the task's broker saw a request
    When a script runs "porelo project unfollow broker --force"
    Then it exits zero

  Scenario: a task started with a refused key fails before any work is done
    Given the suite runs as an unprivileged user
    And the environment variable "PORELO_E2E_REFUSED_KEY" is set
    And the environment variable "PORELO_E2E_MODEL" is set
    Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
    And the vault holds the value of "PORELO_E2E_REFUSED_KEY" as "google" of kind "api-key"
    And a project called "refused" of class "guarded" with a file in it
    When a task is started in "refused" for the "agy" agent and left running
    And the task's container runs:
      """
      timeout 120 agy --model "${PORELO_E2E_MODEL}" --output-format json --dangerously-skip-permissions -p "Reply with the single word PORELONEVER."
      """
    Then it exits non-zero
    And its output does not contain "PORELONEVER"
    And the task's container environment does not contain the value of "PORELO_E2E_REFUSED_KEY"
    When the task's container runs:
      """
      find /home/agent /tmp /workspace -type f ! -name agy -size -2M -exec cat {} + 2>/dev/null || true
      """
    Then its output contains "\"modelProvider\": \"gemini\""
    And its output does not contain the value of "PORELO_E2E_REFUSED_KEY"
    And no log the task left contains the value of "PORELO_E2E_REFUSED_KEY"
    When a script runs "porelo project unfollow refused --force"
    Then it exits zero
