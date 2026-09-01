#!/usr/bin/env python3
"""Run Android → oo-api → PostgreSQL → ConnectOnion → deletion E2E."""

from __future__ import annotations

import argparse
import json
import os
import shlex
import socket
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


TEST_CLASS = "ai.openonion.messages.RealBackendSyncInstrumentedTest"
TEST_RUNNER = "ai.openonion.messages.test/androidx.test.runner.AndroidJUnitRunner"


def run(command: list[str], *, cwd: Path, env: dict[str, str] | None = None) -> None:
    printable = " ".join(command[:4]) + (" …" if len(command) > 4 else "")
    print(f"+ {printable}", flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def json_request(url: str, method: str = "GET") -> dict:
    request = urllib.request.Request(url, method=method)
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.loads(response.read())


def free_port() -> int:
    with socket.socket() as candidate:
        candidate.bind(("127.0.0.1", 0))
        return candidate.getsockname()[1]


def wait_for_server(base_url: str, process: subprocess.Popen) -> None:
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"oo-api E2E server exited with {process.returncode}")
        try:
            if json_request(f"{base_url}/health").get("status") == "ok":
                return
        except OSError:
            time.sleep(0.2)
    raise TimeoutError("oo-api E2E server did not become ready")


def main() -> None:
    project = Path(__file__).resolve().parents[1]
    workspace = project.parent
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn", default=os.environ.get("SMS_INBOX_TEST_DSN"))
    parser.add_argument("--oo-api", type=Path, default=workspace / ".worktree/oo-api-sms")
    parser.add_argument("--connectonion", type=Path, default=workspace / ".worktree/connectonion-sms")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--skip-build", action="store_true")
    args = parser.parse_args()
    if not args.dsn:
        parser.error("--dsn or SMS_INBOX_TEST_DSN is required")

    android_home = Path(os.environ.get("ANDROID_HOME", project / ".tools/android-sdk"))
    java_home = Path(os.environ.get("JAVA_HOME", project / ".tools/jdk/Contents/Home"))
    adb = android_home / "platform-tools/adb"
    oo_python = Path(
        os.environ.get("OO_API_PYTHON", workspace / "oo-api/.venv/bin/python"),
    )
    connect_python = Path(
        os.environ.get("CONNECTONION_PYTHON", workspace / "connectonion/.venv/bin/python"),
    )
    for required in (args.oo_api, args.connectonion, adb, oo_python, connect_python):
        if not required.exists():
            raise FileNotFoundError(required)

    build_env = os.environ.copy()
    build_env.update({"ANDROID_HOME": str(android_home), "JAVA_HOME": str(java_home)})
    adb_prefix = [str(adb)] + (["-s", args.serial] if args.serial else [])
    if not args.skip_build:
        run(
            [str(project / "gradlew"), "assembleDebug", "assembleDebugAndroidTest"],
            cwd=project,
            env=build_env,
        )

    run(adb_prefix + ["install", "-r", str(project / "app/build/outputs/apk/debug/app-debug.apk")], cwd=project)
    run(
        adb_prefix
        + ["install", "-r", str(project / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")],
        cwd=project,
    )

    port = free_port()
    host_base_url = f"http://127.0.0.1:{port}"
    emulator_base_url = f"http://10.0.2.2:{port}"
    server_env = os.environ.copy()
    server_env.update({"SMS_E2E_DSN": args.dsn, "PYTHONPATH": str(args.oo_api)})
    server = subprocess.Popen(
        [
            str(oo_python),
            "-m",
            "uvicorn",
            "tests.e2e_sms_server:app",
            "--host",
            "0.0.0.0",
            "--port",
            str(port),
        ],
        cwd=args.oo_api,
        env=server_env,
    )
    try:
        wait_for_server(host_base_url, server)
        pairing = json_request(
            f"{host_base_url}/test/pairing?version=2",
            method="POST",
        )

        run(
            adb_prefix
            + [
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                f"{TEST_CLASS}#claimSignedPairingWithRealBackend",
                "-e",
                "baseUrl",
                emulator_base_url,
                "-e",
                "pairingLink",
                shlex.quote(pairing["pairing_link"]),
                TEST_RUNNER,
            ],
            cwd=project,
        )

        confirmation = json_request(
            f"{host_base_url}/test/pairing/{pairing['id']}/confirm",
            method="POST",
        )
        assert confirmation["confirmed"] is True, confirmation

        run(
            adb_prefix
            + [
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                f"{TEST_CLASS}#activateAndUploadCiphertextToRealBackend",
                "-e",
                "baseUrl",
                emulator_base_url,
                TEST_RUNNER,
            ],
            cwd=project,
        )

        state_after_upload = json_request(f"{host_base_url}/test/state")
        assert state_after_upload["message_count"] == 1, state_after_upload
        run(
            [
                str(connect_python),
                str(project / "scripts/e2e_agent_assert.py"),
                "read",
                "--backend",
                host_base_url,
                "--connectonion",
                str(args.connectonion),
            ],
            cwd=project,
        )

        run(
            adb_prefix
            + [
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                f"{TEST_CLASS}#deleteCiphertextFromRealBackend",
                "-e",
                "baseUrl",
                emulator_base_url,
                TEST_RUNNER,
            ],
            cwd=project,
        )
        run(
            [
                str(connect_python),
                str(project / "scripts/e2e_agent_assert.py"),
                "empty",
                "--backend",
                host_base_url,
                "--connectonion",
                str(args.connectonion),
            ],
            cwd=project,
        )
        final_state = json_request(f"{host_base_url}/test/state")
        assert final_state["message_count"] == 0, final_state
        print(
            json.dumps(
                {
                    "android_upload": True,
                    "agent_decrypt": True,
                    "android_delete": True,
                    "postgres_message_count": final_state["message_count"],
                },
                sort_keys=True,
            ),
        )
    finally:
        server.terminate()
        try:
            server.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=5)


if __name__ == "__main__":
    main()
