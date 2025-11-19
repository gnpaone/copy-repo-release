#!/bin/sh
set -e

REPO="${GITHUB_ACTION_REPOSITORY}"
TAG="${GITHUB_ACTION_REF}"
TOKEN="$INPUT_GITHUB_TOKEN"

echo "Action repo: $REPO"
echo "Action version (tag): $TAG"

RELEASE_JSON=$(wget -qO- \
  --header="Authorization: Bearer $TOKEN" \
  "https://api.github.com/repos/$REPO/releases/tags/$TAG")

JAR_URL=$(
  printf "%s\n" "$RELEASE_JSON" |
  awk '
    /"url":/ {
      match($0, /"url": *"([^"]+)"/, m)
      if (m[1] != "") last_url = m[1]
    }
    /"name": "app.jar"/ {
      print last_url
    }
  '
)

if [ -z "$JAR_URL" ] || [ "$JAR_URL" = "null" ]; then
  echo "::error::app.jar not found in release $TAG of $REPO"
  exit 1
fi

echo "Downloading app.jar from $REPO@$TAG…"
wget \
  --header="Authorization: Bearer $TOKEN" \
  --header="Accept: application/octet-stream" \
  -O /app/app.jar \
  "$JAR_URL"

echo "Running app.jar"
exec java -jar /app/app.jar
