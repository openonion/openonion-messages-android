#!/usr/bin/env python3
"""Assert Android E2E ciphertext through ConnectOnion's public SMS tool."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from nacl.signing import SigningKey


SEED_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
EXPECTED_SENDER = "+61412345678"
EXPECTED_BODY = "Android to PostgreSQL E2E code 482193"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("read", "empty"))
    parser.add_argument("--backend", required=True)
    parser.add_argument("--connectonion", required=True, type=Path)
    args = parser.parse_args()

    sys.path.insert(0, str(args.connectonion))
    os.environ["CONNECTONION_BACKEND_URL"] = args.backend

    from connectonion.useful_tools import sms

    sms._headers = lambda: {
        "Authorization": "Bearer local-e2e-only",
        "Content-Type": "application/json",
    }
    sms._identity = lambda: {"signing_key": SigningKey(bytes.fromhex(SEED_HEX))}

    messages = sms.get_sms(last=10)
    if args.mode == "read":
        assert len(messages) == 1, messages
        message = messages[0]
        assert message["sender"] == EXPECTED_SENDER, message
        assert message["body"] == EXPECTED_BODY, message
        assert message["trusted"] is False, message
        print(
            json.dumps(
                {
                    "agent_read": True,
                    "server_message_id": message["id"],
                    "trusted": message["trusted"],
                },
                sort_keys=True,
            ),
        )
    else:
        assert messages == [], messages
        print(json.dumps({"agent_inbox_empty": True}, sort_keys=True))


if __name__ == "__main__":
    main()
