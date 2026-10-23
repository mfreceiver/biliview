#!/usr/bin/env bash
# Fail-closed assertions for BiliView's universal release APK.
set -euo pipefail
fail() { echo "ASSERT-FAIL: $*" >&2; exit 1; }
pass() { echo "ASSERT-PASS: $*"; }
APK=${1:?usage: release_assert_apk.sh <apk> <version_name> <version_code> <cert_sha256> [universal]}
EXPECT_NAME=${2:?missing version name}
EXPECT_CODE=${3:?missing version code}
EXPECT_CERT=${4:?missing pinned certificate digest}
EXPECT_ABI=${5:-universal}
[[ "$EXPECT_ABI" == universal ]] || fail "only universal APK is supported"
SDK="$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' local.properties 2>/dev/null | head -1 || true)"
[[ -n "$SDK" && -d "$SDK" ]] || SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -d "$SDK" ]] || fail "Android SDK unavailable"
BT="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
[[ -n "$BT" && -x "$BT/aapt" && -x "$BT/apksigner" ]] || fail "aapt/apksigner unavailable"
command -v python3 >/dev/null || fail "python3 unavailable"
[[ -f "$APK" ]] || fail "APK missing: $APK"
BADGING="$("$BT/aapt" dump badging "$APK")" || fail "aapt badging failed"
"$BT/apksigner" verify "$APK" >/dev/null 2>&1 || fail "apksigner verify failed"
INFO="$("$BT/apksigner" verify --print-certs "$APK" 2>/dev/null)" || fail "certificate extraction failed"
mapfile -t CERTS < <(sed -nE 's/^Signer #1 certificate SHA-256 digest: ([0-9A-Fa-f]{64})$/\1/p' <<<"$INFO")
[[ ${#CERTS[@]} -eq 1 ]] || fail "expected exactly one signer certificate SHA-256"
CERT="${CERTS[0],,}"; EXPECT_CERT="${EXPECT_CERT,,}"
[[ "$CERT" == "$EXPECT_CERT" ]] || fail "certificate mismatch: $CERT != $EXPECT_CERT"
pass "certificate pinned (${CERT:0:12}…)"
grep -qF "package: name='cn.vectory.biliview'" <<<"$BADGING" || fail "package name mismatch"
grep -qF "uses-permission: name='android.permission.INTERNET'" <<<"$BADGING" || fail "INTERNET permission missing"
NAME="$(grep -oP "versionName='\K[^']+" <<<"$BADGING" | head -1)"
CODE="$(grep -oP "versionCode='\K[^']+" <<<"$BADGING" | head -1)"
[[ "$NAME" == "$EXPECT_NAME" ]] || fail "versionName=$NAME expected $EXPECT_NAME"
[[ "$CODE" == "$EXPECT_CODE" ]] || fail "versionCode=$CODE expected $EXPECT_CODE"
python3 - "$APK" "$BADGING" <<'PY' || fail "ABI/native library assertion failed"
import re, sys, zipfile
abis = {'arm64-v8a', 'armeabi-v7a'}
libs = {'libandroidx.graphics.path.so', 'libdatastore_shared_counter.so', 'libffmpegJNI.so', 'libgdx.so'}
def reject(s): sys.exit('ASSERT-FAIL: ' + s)
lines = [x for x in sys.argv[2].splitlines() if x.startswith('native-code:')]
if len(lines) != 1: reject('native-code missing/ambiguous')
reported = set(re.findall(r"'([^']+)'", lines[0]))
try:
 with zipfile.ZipFile(sys.argv[1]) as z:
  paths = [n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
  actual_abis = {p.split('/')[1] for p in paths if len(p.split('/')) == 3}
  if reported != actual_abis: reject(f'aapt/ZIP ABI mismatch: {reported} vs {actual_abis}')
  if actual_abis != abis: reject(f'expected only {sorted(abis)}, got {sorted(actual_abis)}')
  for abi in abis:
   got = {p.split('/')[2] for p in paths if p.startswith(f'lib/{abi}/')}
   if got != libs: reject(f'{abi} library set mismatch: {sorted(got)}')
except (OSError, zipfile.BadZipFile) as e: reject(f'ZIP read error: {e}')
PY
pass "package, INTERNET, version, dual ABI and native library set"
echo "release_assert_apk: ALL PASS ($APK)"
