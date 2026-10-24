#!/usr/bin/env bash
# BiliView release entry point: defaultRelease universal APK only.
set -euo pipefail
PUBLISH=0 PUBLISH_ONLY=0 ALLOW_DIRTY=0
usage() { cat <<'EOF'
Usage: scripts/release_build.sh [--publish | --publish-only] [--allow-dirty]
  default: build, archive, and create annotated local tag (no network writes)
  --publish: build/archive/tag and publish to Gitea
  --publish-only: verify current tagged archive and retry Gitea publishing
  --allow-dirty: build/assert only; never archive/tag/publish
EOF
}
while (($#)); do case "$1" in
 --help|-h) usage; exit 0;; --publish) PUBLISH=1;; --publish-only) PUBLISH_ONLY=1;; --allow-dirty) ALLOW_DIRTY=1;; *) echo "Unknown argument: $1" >&2; usage >&2; exit 2;; esac; shift; done
[[ $((PUBLISH+PUBLISH_ONLY)) -le 1 ]] || { echo '--publish and --publish-only are mutually exclusive' >&2; exit 2; }
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo 'Not in a git worktree' >&2; exit 1; }; cd "$ROOT"
fail() { echo "❌ $*" >&2; exit 1; }
PINNED_CERT_SHA256=156c58b7b1a47bc3651c9cadd2f512faad012b0531b813b27563b950915ff17a
ORIGIN_EXPECTED=http://192.168.3.252:11330/mfreceiver/biliview.git
# Release signing configuration is deliberately separate from local.properties and must never fall back.
PROPS=signing.properties
[[ -f "$PROPS" ]] || fail "missing $PROPS (release signing is fail-closed)"
prop() { sed -nE "s/^$1=(.*)$/\\1/p" "$PROPS" | head -1; }
KS="$(prop storeFile)"; KS_PASS="$(prop storePassword)"; KS_ALIAS="$(prop keyAlias)"
[[ -n "$KS" && -n "$KS_PASS" && "$KS_ALIAS" == release ]] || fail 'signing.properties must define storeFile, storePassword and keyAlias=release'
[[ -r "$KS" ]] || fail "keystore unavailable: $KS"
export KS_PASS
KEYINFO="$(keytool -list -v -keystore "$KS" -storepass:env KS_PASS -alias "$KS_ALIAS" 2>&1)" || fail 'keystore unreadable or release alias missing'
unset KS_PASS
CERT="$(grep -oP 'SHA256: \K[0-9A-Fa-f:]+' <<<"$KEYINFO" | head -1 | tr -d ':' | tr 'A-F' 'a-f')"
[[ -n "$CERT" ]] || fail 'certificate SHA-256 missing (fail-closed)'
[[ "$CERT" == "$PINNED_CERT_SHA256" ]] || fail "certificate pin mismatch: $CERT"
echo "==> Signing preflight passed ($KS, alias=$KS_ALIAS)"
BRANCH="$(git branch --show-current)"; [[ "$BRANCH" == main ]] || fail "release requires main; current=$BRANCH"
DIRTY=0
if [[ -n "$(git status --porcelain)" ]]; then
 if ((PUBLISH || PUBLISH_ONLY)); then git status --short >&2; fail 'publish requires a clean working tree'; fi
 if ((ALLOW_DIRTY)); then DIRTY=1; echo 'WARNING: --allow-dirty: build/assert only; archive/tag disabled'; else git status --short >&2; fail 'release requires a clean working tree'; fi
fi
REMOTE="$(git remote get-url origin 2>/dev/null || true)"
[[ "$REMOTE" == "$ORIGIN_EXPECTED" ]] || fail "origin mismatch: expected $ORIGIN_EXPECTED; got ${REMOTE:-<missing>}"
# Match AppConfiguration version: major.minor.patch[.hotFix].r<count>.<abbrev HEAD>.
CONFIG=buildSrc/src/main/kotlin/AppConfiguration.kt
[[ -f "$CONFIG" ]] || fail "missing $CONFIG"
read_const() { local key="$1"; sed -nE "s/^[[:space:]]*private const val $key = ([0-9]+)$/\\1/p" "$CONFIG"; }
MAJOR="$(read_const major)"; MINOR="$(read_const minor)"; PATCH="$(read_const patch)"; HOTFIX="$(read_const hotFix)"
for n in "$MAJOR" "$MINOR" "$PATCH" "$HOTFIX"; do [[ "$n" =~ ^[0-9]+$ ]] || fail 'version constants missing/ambiguous in AppConfiguration.kt'; done
VCODE="$(git rev-list --count HEAD)"; SHORT="$(git rev-parse --short HEAD)"
VNAME="$MAJOR.$MINOR.$PATCH${HOTFIX:+}"
((HOTFIX == 0)) || VNAME+=".$HOTFIX"
VNAME+=".r$VCODE.$SHORT"
APK_VNAME="$VNAME.release"
TAG="v$VNAME"; ARCHIVE="biliview-$VNAME-$SHORT-universal-release.apk"
# Tag history and archives provide fail-closed monotonicity/uniqueness.
if ((PUBLISH_ONLY)); then
 git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || fail "--publish-only requires local tag $TAG"
 [[ "$(git rev-parse "$TAG^{commit}")" == "$(git rev-parse HEAD)" ]] || fail "tag $TAG does not point to HEAD"
 APK_PATH="APK/$ARCHIVE"; [[ -f "$APK_PATH" ]] || fail "archive missing: $APK_PATH"
 [[ -f APK/SHA256SUMS ]] || fail 'APK/SHA256SUMS missing'
 sha256sum -c APK/SHA256SUMS >/dev/null || fail 'SHA256SUMS verification failed'
 grep -qE "APK/$ARCHIVE\$" APK/SHA256SUMS || fail "SHA256SUMS has no entry for $ARCHIVE"
 MAPPING="APK/biliview-$VNAME-$SHORT-mapping.txt"
 [[ -f "$MAPPING" ]] || fail "mapping archive missing: $MAPPING"
 grep -qE "APK/biliview-$VNAME-$SHORT-mapping\.txt\$" APK/SHA256SUMS || fail "SHA256SUMS has no entry for $MAPPING"
 [[ "$(git cat-file -t "$TAG")" == tag ]] || fail "tag $TAG is not annotated ($(git cat-file -t "$TAG" 2>/dev/null || echo unresolved))"
 NOTE_FILE="APK/biliview-$VNAME-$SHORT.md"; [[ -f "$NOTE_FILE" ]] || fail "release notes missing: $NOTE_FILE"
 scripts/release_assert_apk.sh "$APK_PATH" "$APK_VNAME" "$VCODE" "$CERT" universal
else
 git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && fail "tag already exists: $TAG"
 if ! REMOTE_TAG="$(timeout 30 git ls-remote --tags origin "refs/tags/$TAG" 2>&1)"; then fail 'remote tag query failed (fail-closed)'; fi
 [[ -z "$REMOTE_TAG" ]] || fail "remote tag already exists: $TAG"
 BEST_CODE=0; BEST_SOURCE=''
  track_tag() {
   local name="$1" origin="$2"
   [[ "$name" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)(\.[0-9]+)?\.r([0-9]+)\.([0-9a-f]+)$ ]] || fail "malformed v* tag (fail-closed): $name"
   local code="${BASH_REMATCH[3]}"
   if git rev-parse -q --verify "refs/tags/$name" >/dev/null; then
    local commit actual
    commit="$(git rev-parse "$name^{commit}")" || fail "cannot resolve tag $name"
    actual="$(git rev-list --count "$commit")"
    [[ "$actual" == "$code" ]] || (( 10#$actual == 10#$code )) || fail "tag $name versionCode $code differs from target commit count $actual"
   elif [[ "$origin" == local ]]; then
    fail "cannot resolve local tag $name"
   fi
   # Remote-only tags skip the commit-count consistency check but still count numerically.
   # 10# guards against leading-zero (octal) misinterpretation.
   if (( 10#$code > 10#$BEST_CODE )); then BEST_CODE="$code"; BEST_SOURCE="$origin tag $name"; fi
  }
  while IFS= read -r tag; do track_tag "$tag" local; done < <(git tag -l 'v*')
  REMOTE_TAGS="$(timeout 30 git ls-remote --tags origin 2>&1)" || fail 'remote tag listing failed (fail-closed)'
  while IFS= read -r line; do
   name="${line##*refs/tags/}"
   [[ "$name" == *'^{}' ]] && continue
   [[ -n "$name" ]] || continue
   track_tag "$name" remote
  done <<<"$REMOTE_TAGS"
  SDK="$(sed -nE 's/^sdk\.dir=(.*)$/\1/p' local.properties 2>/dev/null | head -1 || true)"
  [[ -n "$SDK" && -d "$SDK" ]] || SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -d "$SDK" ]] || fail 'Android SDK unavailable for archive history checks'
  BT_DIR="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)"
  [[ -n "$BT_DIR" && -x "$BT_DIR/aapt" ]] || fail 'aapt unavailable for archive history checks'
  for f in APK/biliview-*-universal-release.apk; do
   [[ -f "$f" ]] || continue
   base="$(basename "$f")"
   [[ "$base" =~ ^biliview-([0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?\.r([0-9]+)\.([0-9a-f]+))-[0-9a-f]+-universal-release\.apk$ ]] || continue
   FILE_VERSION="${BASH_REMATCH[1]}"; FILE_CODE="${BASH_REMATCH[3]}"
   (( 10#$FILE_CODE == 10#$VCODE )) && fail "archive versionCode collision: $f"
   BADGING="$("$BT_DIR/aapt" dump badging "$f" 2>/dev/null)" || fail "cannot read archived APK: $f"
   EMBED_NAME="$(grep -oP "versionName='\K[^']+" <<<"$BADGING" | head -1)"
   EMBED_CODE="$(grep -oP "versionCode='\K[^']+" <<<"$BADGING" | head -1)"
   [[ "$EMBED_NAME" == "$FILE_VERSION.release" && "$EMBED_CODE" == "$FILE_CODE" ]] || fail "archive filename/embedded version mismatch: $f ($EMBED_NAME+$EMBED_CODE)"
   if (( 10#$FILE_CODE > 10#$BEST_CODE )); then BEST_CODE="$FILE_CODE"; BEST_SOURCE="$f"; fi
  done
  if (( 10#$BEST_CODE > 0 )); then (( 10#$VCODE > 10#$BEST_CODE )) || fail "versionCode $VCODE is not greater than historical maximum $BEST_CODE ($BEST_SOURCE)"; fi
  for f in APK/biliview-*.apk; do [[ -e "$f" ]] || continue; [[ "$(basename "$f")" != *"$VNAME"* ]] || fail "archive version already exists: $f"; done
  echo "==> Version $APK_VNAME (versionCode=$VCODE; tag=$TAG)"
 ./gradlew :app:assembleDefaultRelease --console=plain
 # BuildType/flavor layout is intentionally discovered, not hardcoded. Accept only one default release universal artifact.
 mapfile -t FOUND < <(find app/build/outputs/apk -type f -name 'BiliView_*.release_default_universal.apk' -print 2>/dev/null | sort)
 ((${#FOUND[@]} == 1)) || fail "expected exactly one defaultRelease universal APK, found ${#FOUND[@]}"
 SRC="${FOUND[0]}"
 scripts/release_assert_apk.sh "$SRC" "$APK_VNAME" "$VCODE" "$CERT" universal
 if ((DIRTY)); then echo '✅ Build validation passed; dirty-tree mode skipped archive/tag/publish'; exit 0; fi
 mkdir -p APK
 cp "$SRC" "APK/$ARCHIVE"
 # Archive the R8 mapping alongside the APK (path discovered, same discipline as the APK).
 mapfile -t MAPPING_FOUND < <(find app/build/outputs -type f -name mapping.txt -path '*default/release/*' -print 2>/dev/null | sort)
 ((${#MAPPING_FOUND[@]} == 1)) || fail "expected exactly one defaultRelease mapping.txt, found ${#MAPPING_FOUND[@]}"
 cp "${MAPPING_FOUND[0]}" "APK/biliview-$VNAME-$SHORT-mapping.txt"
 # Keep prior version archives; regenerate checksums over strict BiliView release archive names.
 : > APK/SHA256SUMS
 for f in APK/biliview-*-universal-release.apk APK/biliview-*-mapping.txt; do [[ -f "$f" ]] || continue; sha256sum "$f" >> APK/SHA256SUMS; done
 PREV="$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
 RANGE="${PREV:+$PREV..}HEAD"
 NOTE_FILE="APK/biliview-$VNAME-$SHORT.md"
 { printf 'Release %s (%s+%s @ %s)\n\n- %s\n\n### Commits\n' "$TAG" "$APK_VNAME" "$VCODE" "$SHORT" "$ARCHIVE"; git log --no-merges --pretty='- %s' "$RANGE"; } > "$NOTE_FILE"
 git tag -a "$TAG" -F "$NOTE_FILE"
 echo "✅ Archived and tagged $TAG"
fi
if ((PUBLISH || PUBLISH_ONLY)); then
 # Credentials are fetched non-interactively before any network write.
 export GIT_TERMINAL_PROMPT=0 GIT_ASKPASS=true
 CRED="$(printf 'protocol=http\nhost=192.168.3.252:11330\n\n' | timeout 15 git credential fill)" || fail 'Gitea credentials unavailable (git credential fill)'
 USERNAME="$(sed -n 's/^username=//p' <<<"$CRED")"; PASSWORD="$(sed -n 's/^password=//p' <<<"$CRED")"
 [[ -n "$USERNAME" && -n "$PASSWORD" ]] || fail 'Gitea credentials incomplete'
 API=http://192.168.3.252:11330/api/v1/repos/mfreceiver/biliview
 curl_config() { printf '%s\0%s\0' "$USERNAME" "$PASSWORD" | python3 -c 'import json,sys; u,p,_=sys.stdin.buffer.read().decode().split("\0",2); print("user = "+json.dumps(u+":"+p))'; }
  curl_auth() { curl_config | curl -K - -sS --fail-with-body --connect-timeout 10 --max-time 120 "$@"; }
 # Push the already-verified commit/tag; Gitea release/asset creation is repeatable on --publish-only.
 timeout 120 git push origin main
 timeout 120 git push origin "$TAG"
 RELEASE_JSON="$(mktemp)"; trap 'rm -f "$RELEASE_JSON"' EXIT
 STATUS="$(curl_config | curl -K - -sS --connect-timeout 10 --max-time 120 -o "$RELEASE_JSON" -w '%{http_code}' "$API/releases/tags/$TAG")" || fail 'Gitea release lookup failed'
 if [[ "$STATUS" == 404 ]]; then
   BODY="$(python3 -c 'import json,sys; print(json.dumps({"tag_name":sys.argv[1],"name":sys.argv[1],"body":open(sys.argv[2],encoding="utf8").read()}))' "$TAG" "$NOTE_FILE")"
   STATUS="$(curl_auth -o "$RELEASE_JSON" -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d "$BODY" "$API/releases")" || fail 'Gitea release creation failed'
   [[ "$STATUS" =~ ^2 ]] || fail "Gitea release creation HTTP $STATUS"
 fi
 REL_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("id", ""))' "$RELEASE_JSON")"
 [[ -n "$REL_ID" ]] || fail 'Gitea release response has no id'
 # Upload only if no same-name asset is present; existing assets are never overwritten.
 ASSET_STATUS="$(curl_auth -o "$RELEASE_JSON" -w '%{http_code}' "$API/releases/$REL_ID/assets")" || fail 'Gitea asset listing failed'
  [[ "$ASSET_STATUS" =~ ^2 ]] || fail "Gitea asset listing HTTP $ASSET_STATUS"
  LOCAL_SHA="$(sha256sum "APK/$ARCHIVE" | cut -d' ' -f1)"
  LOCAL_SIZE="$(stat -c%s "APK/$ARCHIVE")"
  verify_asset_listing() {
    python3 - "$RELEASE_JSON" "$ARCHIVE" <<'PYASSET'
import json,sys
assets=json.load(open(sys.argv[1],encoding='utf8'))
if not isinstance(assets,list): sys.exit('asset list is not an array')
for a in assets:
 if a.get('name') == sys.argv[2]:
  print(json.dumps({'size':a.get('size'), 'sha256':a.get('sha256') or '', 'url':a.get('browser_download_url') or ''})); break
else: sys.exit(3)
PYASSET
  }
  if EXISTING="$(verify_asset_listing)"; then
    REMOTE_SIZE="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("size", ""))' "$EXISTING")"
    [[ "$REMOTE_SIZE" == "$LOCAL_SIZE" ]] || fail "asset size mismatch for $ARCHIVE: remote=$REMOTE_SIZE local=$LOCAL_SIZE"
    REMOTE_SHA="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("sha256", ""))' "$EXISTING")"
    if [[ -z "$REMOTE_SHA" ]]; then
      ASSET_URL="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("url", ""))' "$EXISTING")"
      [[ -n "$ASSET_URL" ]] || fail "existing asset has no sha256 or download URL: $ARCHIVE"
      DOWNLOAD="$(mktemp)"
      HTTP="$(curl_auth --max-time 300 -L -o "$DOWNLOAD" -w '%{http_code}' "$ASSET_URL")" || { rm -f "$DOWNLOAD"; fail "remote asset download failed: $ARCHIVE"; }
      [[ "$HTTP" =~ ^2 ]] || { rm -f "$DOWNLOAD"; fail "remote asset download HTTP $HTTP"; }
      REMOTE_SHA="$(sha256sum "$DOWNLOAD" | cut -d' ' -f1)"; rm -f "$DOWNLOAD"
    fi
    [[ "$REMOTE_SHA" == "$LOCAL_SHA" ]] || fail "asset SHA256 mismatch for $ARCHIVE: remote=$REMOTE_SHA local=$LOCAL_SHA"
    echo "==> Existing asset verified by name, size and SHA256: $ARCHIVE"
  else
    STATUS="$(curl_auth -o "$RELEASE_JSON" -w '%{http_code}' -X POST "$API/releases/$REL_ID/assets?name=$ARCHIVE" -F "attachment=@APK/$ARCHIVE")" || fail 'Gitea asset upload failed'
    [[ "$STATUS" =~ ^2 ]] || fail "Gitea asset upload HTTP $STATUS"
    ASSET_STATUS="$(curl_auth -o "$RELEASE_JSON" -w '%{http_code}' "$API/releases/$REL_ID/assets")" || fail 'post-upload asset listing failed'
    [[ "$ASSET_STATUS" =~ ^2 ]] || fail "post-upload asset listing HTTP $ASSET_STATUS"
    EXISTING="$(verify_asset_listing)" || fail "uploaded asset missing from final listing: $ARCHIVE"
    REMOTE_SIZE="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("size", ""))' "$EXISTING")"
    REMOTE_SHA="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("sha256", ""))' "$EXISTING")"
    if [[ -z "$REMOTE_SHA" ]]; then
      ASSET_URL="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("url", ""))' "$EXISTING")"
      [[ -n "$ASSET_URL" ]] || fail "uploaded asset has no sha256 or download URL: $ARCHIVE"
      DOWNLOAD="$(mktemp)"
      HTTP="$(curl_auth --max-time 300 -L -o "$DOWNLOAD" -w '%{http_code}' "$ASSET_URL")" || { rm -f "$DOWNLOAD"; fail "uploaded asset download failed: $ARCHIVE"; }
      [[ "$HTTP" =~ ^2 ]] || { rm -f "$DOWNLOAD"; fail "uploaded asset download HTTP $HTTP"; }
      REMOTE_SHA="$(sha256sum "$DOWNLOAD" | cut -d' ' -f1)"; rm -f "$DOWNLOAD"
    fi
    [[ "$REMOTE_SIZE" == "$LOCAL_SIZE" && "$REMOTE_SHA" == "$LOCAL_SHA" ]] || fail "post-upload asset size/SHA256 mismatch for $ARCHIVE (size=$REMOTE_SIZE sha=$REMOTE_SHA)"
  fi
  echo "✅ Published $TAG: http://192.168.3.252:11330/mfreceiver/biliview/releases/tag/$TAG"
else
 echo "Release ready locally: $TAG; archive APK/$ARCHIVE; notes $NOTE_FILE"
fi
