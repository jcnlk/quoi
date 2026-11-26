#!/usr/bin/env bash
set -euo pipefail

checker_version="v4.0.2"
checker_sha256="44efaef3ed14c6ab52eb87481d78e5e5c204796dccf26e2f4598a61ba17f8003"
checker_dir=$(mktemp -d)
trap 'rm -rf "$checker_dir"' EXIT

checker_archive="$checker_dir/editorconfig-checker.tar.gz"
curl --fail --silent --show-error --location --retry 2 --max-time 30 \
    --output "$checker_archive" \
    "https://github.com/editorconfig-checker/editorconfig-checker/releases/download/$checker_version/editorconfig-checker-linux-amd64.tar.gz"
printf '%s  %s\n' "$checker_sha256" "$checker_archive" | sha256sum --check
tar -xzf "$checker_archive" -C "$checker_dir" editorconfig-checker

"$checker_dir/editorconfig-checker" "$@"
