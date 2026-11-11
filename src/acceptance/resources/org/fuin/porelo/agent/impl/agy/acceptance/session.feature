@credential
Feature: An unattended task continues its recorded conversation

    Background:
        Given the suite runs as an unprivileged user
        And the environment variable "GEMINI_API_KEY" is set
        And the environment variable "PORELO_E2E_MODEL" is set

    @slow
    Scenario: a task that allows no subagents has the start of one denied
        Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds the value of "GEMINI_API_KEY" as "google" of kind "api-key"
        And a project called "alone" of class "guarded" with a file in it
        When a script runs "timeout 300 porelo task start solo --project alone --repository alone --agent agy --provider google --model ${PORELO_E2E_MODEL} --prompt 'Start a subagent now with invoke_subagent and tell it to answer with one word of your choice. If that call is denied or fails, answer with the word MISSING and try nothing else; if it worked, answer with the word STARTED.'"
        Then it exits zero
        And its output contains "MISSING"
        And its output does not contain "STARTED"
        When a script runs "porelo project remove alone --force"
        Then it exits zero

    @slow
    Scenario: An unattended task remembers across a stop and restart
        Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        And the vault holds the value of "GEMINI_API_KEY" as "google" of kind "api-key"
        And a project called "cont" of class "guarded" with a file in it
        When a script runs "timeout 120 porelo task start talk --project cont --repository cont --agent agy --provider google --model ${PORELO_E2E_MODEL} --prompt 'Remember the word PAPAYA. Reply with OK only.'"
        Then it exits zero
        And porelo says task "talk" of "cont" has a session to continue
        When a script runs "porelo task stop talk"
        And a script runs "timeout 120 porelo task start talk --project cont --repository cont --agent agy --provider google --model ${PORELO_E2E_MODEL} --prompt 'Which word did I ask you to remember? Reply with the word only.'"
        Then it exits zero
        And its output contains "session   continuing"
        And its output contains "PAPAYA"
        When a script runs "porelo project remove cont --force"
        Then it exits zero
