#!/usr/bin/env bash
set -euo pipefail

package="ai.openonion.messages"
apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
marker="OpenOnion runtime test $RANDOM"
if [[ -n "${ANDROID_HOME:-}" ]]; then
    adb_cmd="$ANDROID_HOME/platform-tools/adb"
else
    adb_cmd="adb"
fi

"$adb_cmd" install -r "$apk"
"$adb_cmd" shell cmd role add-role-holder android.app.role.SMS "$package"
"$adb_cmd" emu sms send +61412345678 "$marker"

for _ in {1..20}; do
    if "$adb_cmd" shell content query --uri content://sms/inbox --projection address:body \
        | grep -F "$marker" >/dev/null; then
        echo "Verified default SMS receipt: $marker"
        exit 0
    fi
    sleep 1
done

echo "SMS was not persisted by the default handler" >&2
exit 1
