#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p repos
cd repos

function prepare_repo() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: prepare_repo <repo>"
    exit 1
  fi
  local repo=$1
  local dir
  dir=$(basename -s .git "$(git ls-remote --get-url "$repo")")
  echo "Preparing repository: $repo"
  if [[ ! -d $dir ]]; then
    echo "Cloning..."
    git clone "$repo"
  else
    echo "Directory '$dir' already exists. Pulling latest changes..."
    git -C "$dir" pull
  fi
}

prepare_repo https://gitcode.com/openharmony/applications_app_samples
prepare_repo https://gitcode.com/openharmony/applications_calendar_data
prepare_repo https://gitcode.com/openharmony/applications_call
prepare_repo https://gitcode.com/openharmony/applications_contacts
prepare_repo https://gitcode.com/openharmony/applications_filepicker
prepare_repo https://gitcode.com/openharmony/applications_hap
prepare_repo https://gitcode.com/openharmony/applications_launcher
prepare_repo https://gitcode.com/openharmony/applications_mms
prepare_repo https://gitcode.com/openharmony/applications_notes
prepare_repo https://gitcode.com/openharmony/applications_print_spooler
prepare_repo https://gitcode.com/openharmony/applications_screenlock
prepare_repo https://gitcode.com/openharmony/applications_settings
prepare_repo https://gitcode.com/openharmony/applications_settings_data
prepare_repo https://gitcode.com/openharmony/applications_systemui
