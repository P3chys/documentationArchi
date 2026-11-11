#!/usr/bin/env bash
# Usage: next-version.sh <none|patch|minor|major>
# Prints "previous=<latest release>" and "version=<version to release>" (empty: nothing to release), for $GITHUB_OUTPUT.
#   - version in gradle.properties ahead of the latest v* tag: release it as it is (bumped by hand in the pull request)
#   - otherwise: bump the latest tag by the given part, or release nothing for "none"
set -euo pipefail
bump=${1:-none}

latest=$(git tag --list 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -1)
latest=${latest#v}
latest=${latest:-0.0.0}
current=$(sed -n 's/^version=//p' gradle.properties | tr -d '[:space:]')

if [ "$current" != "$latest" ] && [ "$(printf '%s\n%s\n' "$latest" "$current" | sort -V | tail -1)" = "$current" ]; then
  next=$current
else
  IFS=. read -r major minor patch <<<"$latest"
  case "$bump" in
    major) next="$((major + 1)).0.0" ;;
    minor) next="$major.$((minor + 1)).0" ;;
    patch) next="$major.$minor.$((patch + 1))" ;;
    none) next= ;;
    *) echo "unknown bump: $bump" >&2; exit 2 ;;
  esac
fi

if [ -n "$next" ] && git rev-parse -q --verify "refs/tags/v$next" >/dev/null; then
  echo "tag v$next already exists" >&2; exit 1
fi
echo "previous=$latest"
echo "version=$next"
