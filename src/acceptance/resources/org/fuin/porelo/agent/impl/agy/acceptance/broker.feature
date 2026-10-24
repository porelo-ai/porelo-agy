Feature: The brokering path works end to end, without an account

    The vault holds a deliberately fake Google API key, so the provider answers with an error.
    That rejection is the proof, not the failure: it is the provider rejecting a bad key rather
    than the proxy rejecting the token, so the relay carried the request and the broker swapped the
    credential in on the way out. Only a 200 needs an account, which is what the credential feature
    is for.

    The fake key must not look like a real one: it is searched for inside the container.

    Background:
        Given the suite runs as an unprivileged user

    @slow
    Scenario: the agent is pointed at the broker, holds only a task token, and its requests go through
        Given a vault of this scenario's own, unlocked with the passphrase "scenario-vault-passphrase"
        When a script runs "printf porelo-agy-check-not-a-real-key | porelo vault put google --type api-key"
        Then it exits zero
        Given a project called "brokercheck" of class "guarded" with a file in it
        When a task is started in "brokercheck" for the "agy" agent through "google" and left running
        # The agent's own setup file, which selects the Gemini provider mode and points at the broker.
        And the task's container runs:
            """
            stat -c '%a %U' /home/agent/.gemini/antigravity-cli/settings.json
            """
        Then its output contains "600 agent"
        When the task's container runs:
            """
            cat /home/agent/.gemini/antigravity-cli/settings.json; env
            """
        Then its output contains "porelo_pt_"
        And its output does not contain "porelo-agy-check-not-a-real-key"
        # The provider rejection that follows is Google's, so the command's own exit says nothing here.
        When the task's container runs:
            """
            timeout 240 agy --output-format json --dangerously-skip-permissions --model gemini-3.6-flash-low -p "Reply with exactly the word PORELOLIVE and nothing else." || true
            """
        Then the task's broker saw a request
        And its output contains "from the provider"
        When a script runs "porelo project unfollow brokercheck --force"
        Then it exits zero
