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
  ./scripts/verify-real-e2e.py
```

The harness creates and later drops a random PostgreSQL schema. It uses the
signed v2 pairing routes, Android Keystore device proof, Agent confirmation,
real device credentials, Android's production crypto code, the production
delivery worker, and ConnectOnion's public `get_sms()` helper. It also deletes
from Android and asserts the PostgreSQL message count returns to zero. The fixed
sender, body, address, and private seed are public test vectors. The debug
manifest alone permits cleartext HTTP to the emulator loopback bridge; release
builds continue to reject cleartext traffic.
