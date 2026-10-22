Feature: Safe refusals before a task has a credential

    Background:
        Given the suite runs as an unprivileged user

    Scenario: An undeclared login cannot run a guessed authentication command
        When logging in to the "agy" agent is attempted
        Then it exits non-zero

    Scenario: The deliberately refused telemetry host is visible
        Given a terminal on the machine
        When I run "porelo agents --verbose"
        Then the terminal shows "play.googleapis.com"

    Scenario: Missing authentication is refused before building a task
        Given a project called "nocred" of class "guarded" with a file in it
        And a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds no credential for "google"
        When a task nobody is watching is started in "nocred" for the "agy" agent
        Then its output mentions one of "no credential, is locked, cannot authenticate"
        And its output contains "google"

    Scenario: A script is not asked an interactive launch question
        Given a project called "noask" of class "guarded" with a file in it
        When a script asks what a task in "noask" for the "agy" agent would do
        Then its output does not contain "[Y/n]"
        And its output contains no escape sequences
