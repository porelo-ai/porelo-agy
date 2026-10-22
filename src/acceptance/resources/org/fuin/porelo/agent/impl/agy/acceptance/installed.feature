Feature: The provider-independent adapter on a machine

    Background:
        Given the suite runs as an unprivileged user

    Scenario: Porelo discovers the separately installed adapter
        When a script runs "porelo --version"
        Then it exits zero
        And a script running "porelo agents" mentions "agy"

    Scenario: A terminal lists the adapter by its label
        Given a terminal on the machine
        When I run "porelo agents"
        Then the terminal shows "Antigravity CLI"

    Scenario: An update run installs the version it was asked to test
        Given the environment variable "PORELO_E2E_EXPECT_CLI" is set
        Then the "agy" agent installs the version in the environment variable "PORELO_E2E_EXPECT_CLI"
