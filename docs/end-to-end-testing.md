# Real end-to-end test

The release gate exercises the real cross-repository path rather than replacing
the backend with MockWebServer:

```text
Agent-signed QR → Android device proof → Agent-signed confirmation
                                      ↓
Kotlin encryption + Worker → FastAPI SMS routes → PostgreSQL
                                           ↓
ConnectOnion get_sms() ← HTTP ciphertext ←─┘
          ↓
Android device deletion → PostgreSQL row count = 0
```

Requirements are an API 26+ emulator, the Android toolchain, sibling checkouts
of `oo-api` and `connectonion`, and a disposable PostgreSQL database. Never use
a production database or real SMS in this test.

```bash
SMS_INBOX_TEST_DSN='postgresql://localhost/openonion_test' \
  ./scripts/verify-real-e2e.py \
    --oo-api ../oo-api \
    --connectonion ../connectonion
```

Use checkouts containing the signed v2 pairing implementation; old feature
branches may only implement v1. The explicit checkout arguments above avoid
depending on the harness's historical `.worktree/` defaults.

The harness creates and later drops a random PostgreSQL schema. It uses the
signed v2 pairing routes, Android Keystore device proof, Agent confirmation,
real device credentials, Android's production crypto code, the production
delivery worker, and ConnectOnion's public `get_sms()` helper. It also deletes
from Android and asserts the PostgreSQL message count returns to zero. The fixed
sender, body, address, and private seed are public test vectors. The debug
manifest alone permits cleartext HTTP to the emulator loopback bridge; release
builds continue to reject cleartext traffic.

## Hosted release gate

The `SMS Android E2E` workflow in `openonion/oo-api` runs the same harness
against an isolated PostgreSQL 16 service and an API 35 Android emulator:

```bash
gh workflow run sms-android-e2e.yml --repo openonion/oo-api --ref main
gh run list --repo openonion/oo-api --workflow sms-android-e2e.yml --limit 1
```

Confirm the Android checkout matches the intended release candidate. Success
must include the harness's final `android_upload`, `agent_decrypt`, and
`android_delete` assertions and `postgres_message_count: 0`. The test schema
is dropped when the fixture server shuts down.

The ordinary `connectedDebugAndroidTest` suite is a different gate: without
backend arguments it skips the three real-backend methods. Do not count those
skips as successful end-to-end coverage.
