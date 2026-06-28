#!/usr/bin/env bash
set -euo pipefail

# Maven Central Portal Publisher API는 Maven repository layout 전체를 하나의 zip bundle로 받는다.
# 이 스크립트는 Gradle이 만든 staging repository를 검증하고, 누락된 checksum을 보강한 뒤 업로드용 zip을 만든다.

usage() {
  cat <<'USAGE'
Usage: build-central-portal-bundle.sh <staging-directory> <bundle-zip-path>

Arguments:
  staging-directory  Gradle maven-publish가 생성한 CentralPortalStaging repository directory
  bundle-zip-path    생성할 Maven Central deployment bundle zip path
USAGE
}

if [[ $# -ne 2 ]]; then
  usage >&2
  exit 64
fi

staging_dir="$1"
bundle_path="$2"

if [[ "${bundle_path}" != /* ]]; then
  bundle_path="$(pwd)/${bundle_path}"
fi

if [[ ! -d "${staging_dir}" ]]; then
  echo "Staging directory does not exist: ${staging_dir}" >&2
  exit 66
fi

mkdir -p "$(dirname "${bundle_path}")"
rm -f "${bundle_path}"

is_checksum_file() {
  case "$1" in
    *.md5|*.sha1|*.sha256|*.sha512) return 0 ;;
    *) return 1 ;;
  esac
}

requires_signature() {
  case "$(basename "$1")" in
    maven-metadata.xml) return 1 ;;
    *) return 0 ;;
  esac
}

write_checksum() {
  local algorithm="$1"
  local file_path="$2"
  local checksum_path="${file_path}.${algorithm}"

  if [[ -f "${checksum_path}" ]]; then
    return 0
  fi

  case "${algorithm}" in
    md5)
      openssl dgst -md5 -r "${file_path}" | awk '{print $1}' > "${checksum_path}"
      ;;
    sha1)
      openssl dgst -sha1 -r "${file_path}" | awk '{print $1}' > "${checksum_path}"
      ;;
    *)
      echo "Unsupported checksum algorithm: ${algorithm}" >&2
      exit 64
      ;;
  esac
}

missing_signatures=0
while IFS= read -r -d '' file_path; do
  if is_checksum_file "${file_path}" || [[ "${file_path}" == *.asc ]]; then
    continue
  fi

  write_checksum md5 "${file_path}"
  write_checksum sha1 "${file_path}"

  if requires_signature "${file_path}" && [[ ! -f "${file_path}.asc" ]]; then
    echo "Missing GPG signature: ${file_path}.asc" >&2
    missing_signatures=1
  fi
done < <(find "${staging_dir}" -type f -print0)

if [[ "${missing_signatures}" -ne 0 ]]; then
  echo "Central bundle validation failed because one or more signatures are missing." >&2
  exit 65
fi

(
  cd "${staging_dir}"
  zip -qr "${bundle_path}" .
)

echo "Created Central Portal bundle: ${bundle_path}" >&2
