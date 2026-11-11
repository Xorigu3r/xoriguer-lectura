#!/usr/bin/env bash
# Crea una Release a GitHub i hi puja dist/LecturaXoriguer.apk i dist/LecturaXoriguer-Windows.zip.
# Ús: bash empaquetar/publica_release.sh v0.1.1 "Títol" fitxer_notes.md
# Fa servir el token de GitHub que ja té guardat git (git credential fill). No cal gh.
set -euo pipefail
TAG="$1"; TITOL="$2"; NOTES="$3"
ARREL="$(cd "$(dirname "$0")/.." && pwd)"
TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill 2>/dev/null | sed -n 's/^password=//p')
[ -n "$TOKEN" ] || { echo "No hi ha token de GitHub"; exit 1; }
TMP=$(mktemp -d)
python -I - "$TAG" "$TITOL" "$NOTES" "$TMP/rel.json" <<'PY'
import json, sys
tag, titol, notes, out = sys.argv[1:]
json.dump({"tag_name": tag, "target_commitish": "main", "name": titol, "make_latest": "true",
           "body": open(notes, encoding="utf-8").read()}, open(out, "w", encoding="utf-8"), ensure_ascii=False)
PY
H=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")
curl -sS -o "$TMP/resp.json" -w "release %{http_code}\n" "${H[@]}" -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @"$TMP/rel.json" https://api.github.com/repos/Xorigu3r/xoriguer-lectura/releases
ID=$(python -I -c "import json,sys;print(json.load(open(sys.argv[1],encoding='utf-8'))['id'])" "$TMP/resp.json")
for f in LecturaXoriguer.apk LecturaXoriguer-Windows.zip; do
  case $f in *.apk) T=application/vnd.android.package-archive;; *) T=application/zip;; esac
  curl -sS -o /dev/null -w "$f %{http_code}\n" "${H[@]}" -H "Content-Type: $T" --data-binary @"$ARREL/dist/$f" \
    "https://uploads.github.com/repos/Xorigu3r/xoriguer-lectura/releases/$ID/assets?name=$f"
done
# Comprova que el que es baixa és idèntic
for f in LecturaXoriguer.apk LecturaXoriguer-Windows.zip; do
  curl -sSfL -o "$TMP/$f" "https://github.com/Xorigu3r/xoriguer-lectura/releases/download/$TAG/$f"
  a=$(sha256sum "$ARREL/dist/$f" | cut -c1-16); b=$(sha256sum "$TMP/$f" | cut -c1-16)
  [ "$a" = "$b" ] && echo "$f idèntic ($a)" || { echo "$f DIFERENT"; exit 1; }
done
rm -rf "$TMP"
