#!/usr/bin/env bash
set -euo pipefail

VERSION_FILE="SVU.md"
VERSION_PREFIX="Demeter-"
COMMIT_PREFIX="Actualización versión"

main() {
  if [[ ! -f "$VERSION_FILE" ]]; then
    echo "ERROR: no existe '$VERSION_FILE' en $(pwd)" >&2
    return 1
  fi

  local CURRENT
  CURRENT="$(grep -oE "${VERSION_PREFIX}[0-9]+" "$VERSION_FILE" | head -n1 || true)"

  if [[ -z "$CURRENT" ]]; then
    echo "ERROR: no se encontró '${VERSION_PREFIX}N' en $VERSION_FILE" >&2
    return 1
  fi

  local CURRENT_NUM="${CURRENT#${VERSION_PREFIX}}"
  local NEW_NUM=$((CURRENT_NUM + 1))
  local NEW_VERSION="${VERSION_PREFIX}${NEW_NUM}"

  sed -i "s|\*\*Versión actual:\*\* ${CURRENT}|**Versión actual:** ${NEW_VERSION}|" "$VERSION_FILE"
  printf -- '- %s\n' "$NEW_VERSION" >> "$VERSION_FILE"

  git add .
  git commit -m "${COMMIT_PREFIX} ${NEW_VERSION}"
  git push

  echo "✔ Subido ${CURRENT} → ${NEW_VERSION}"
}

main "$@"
