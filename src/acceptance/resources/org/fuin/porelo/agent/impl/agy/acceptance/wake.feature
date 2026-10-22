@credential
Feature: A mailbox message wakes the composer without interrupting work

    Background:
        Given the suite runs as an unprivileged user
        And the environment variable "GEMINI_API_KEY" is set
        And the environment variable "PORELO_E2E_MODEL" is set

    @slow
    Scenario: A message to an idle task obtains a computed answer
        Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds the value of "GEMINI_API_KEY" as "google" of kind "api-key"
        And a daemon of this scenario's own
        And a project called "wake" of class "guarded" with a file in it
        And a terminal on the machine
        When I run "porelo task start wake --project wake --repository wake --agent agy --provider google --model ${PORELO_E2E_MODEL} --clearance deny"
        Then the "agy" agent in task "wake" of "wake" reaches work without being asked anything
        When a script runs "echo 'What is 1234 plus 4321? Reply with the digits only.' | porelo talk tell porelo-wake-wake"
        Then it exits zero
        And within 120 seconds this script exits zero:
            """
            held=$(porelo talk held porelo-wake-wake)
            for id in $(printf '%s\n' "$held" | awk '$1 ~ /\.json$/ && $2 == "held" {print $1}'); do
              porelo talk read porelo-wake-wake "$id" | grep -qE '5,?555' && exit 0
            done
            podman exec porelo-wake-wake tmux capture-pane -p -S - -t porelo | grep -qE '5,?555'
            """
        When a script runs "porelo task remove porelo-wake-wake --force"
        And a script runs "porelo project unfollow wake --force"
        Then it exits zero

    @slow
    Scenario: A message arriving during work is handled after the turn
        Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds the value of "GEMINI_API_KEY" as "google" of kind "api-key"
        And a daemon of this scenario's own
        And a project called "busy" of class "guarded" with a file in it
        And a terminal on the machine
        When I run "porelo task start busy --project busy --repository busy --agent agy --provider google --model ${PORELO_E2E_MODEL} --clearance deny"
        Then the "agy" agent in task "busy" of "busy" reaches work without being asked anything
        When I type "Run the shell command 'sleep 10' with your shell tool, then reply with the single word DONE."
        And I press Enter
        Then within 60 seconds this script exits zero:
            """
            podman exec porelo-busy-busy tmux capture-pane -p -t porelo | grep -qF 'esc to cancel'
            """
        When a script runs "echo 'What is 1234 plus 4321? Reply with the digits only.' | porelo talk tell porelo-busy-busy"
        Then it exits zero
        And within 120 seconds this script exits zero:
            """
            held=$(porelo talk held porelo-busy-busy)
            for id in $(printf '%s\n' "$held" | awk '$1 ~ /\.json$/ && $2 == "held" {print $1}'); do
              porelo talk read porelo-busy-busy "$id" | grep -qE '5,?555' && exit 0
            done
            podman exec porelo-busy-busy tmux capture-pane -p -S - -t porelo | grep -qE '5,?555'
            """
        When a script runs "porelo task remove porelo-busy-busy --force"
        And a script runs "porelo project unfollow busy --force"
        Then it exits zero
