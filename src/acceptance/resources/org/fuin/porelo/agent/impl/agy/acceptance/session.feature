@credential
Feature: An unattended task continues its recorded conversation

    Background:
        Given the suite runs as an unprivileged user
        And the environment variable "GEMINI_API_KEY" is set
        And the environment variable "PORELO_E2E_MODEL" is set

    @slow
    Scenario: An unattended task remembers across a stop and restart
        Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds the value of "GEMINI_API_KEY" as "google" of kind "api-key"
        And a project called "cont" of class "guarded" with a file in it
        When a script runs "timeout 120 porelo task start talk --project cont --repository cont --agent agy --provider google --model ${PORELO_E2E_MODEL} --prompt 'Remember the word PAPAYA. Reply with OK only.'"
        Then it exits zero
        And porelo says task "talk" of "cont" has a session to continue
        When a script runs "porelo task stop porelo-cont-talk"
        And a script runs "timeout 120 porelo task start talk --project cont --repository cont --agent agy --provider google --model ${PORELO_E2E_MODEL} --prompt 'Which word did I ask you to remember? Reply with the word only.'"
        Then it exits zero
        And its output contains "session   continuing"
        And its output contains "PAPAYA"
        When a script runs "porelo project unfollow cont --force"
        Then it exits zero
