Feature: Safe refusals before a task has a credential

    Background:
        Given the suite runs as an unprivileged user

    Scenario: Previewing a login does not start authentication
        When a script runs "porelo vault login agy --dry-run"
        Then it exits zero
        And its output contains "previewed nothing was built or run"

    Scenario: The deliberately refused telemetry host is visible
        Given a terminal on the machine
        When I run "porelo agents --verbose"
        Then the terminal shows "play.googleapis.com"
        And the terminal shows "www.googleapis.com"
        And the terminal shows "antigravity-unleash.goog"

    Scenario: Missing authentication is refused before building a task
        Given a project called "nocred" of class "guarded" with a file in it
        And a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds no credential for "google-sign-in"
        When a task nobody is watching is started in "nocred" for the "agy" agent
        Then its output mentions one of "no credential, is locked, cannot authenticate"
        And its output contains "google-sign-in"

    Scenario: A script is not asked an interactive launch question
        Given a project called "noask" of class "guarded" with a file in it
        When a script asks what a task in "noask" for the "agy" agent would do
        Then its output does not contain "[Y/n]"
        And its output contains no escape sequences
