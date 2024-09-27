#!/bin/bash
set -euo pipefail

cd "$(dirname $0)"
mkdir -p repos
cd repos

function prepare_repo() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: prepare_repo <repo>"
    exit 1
  fi
  REPO=$1
  DIR=$(basename -s .git $(git ls-remote --get-url $REPO))
  if [[ ! -d $DIR ]]; then
    echo "Cloning repository: $REPO"
    git clone $REPO
  else
    echo "Pulling latest changes: $REPO"
    git -C $DIR pull
  fi
}

prepare_repo https://gitee.com/openharmony/applications_app_samples
prepare_repo https://gitee.com/openharmony/applications_filepicker
prepare_repo https://gitee.com/openharmony/applications_hap
prepare_repo https://gitee.com/openharmony/applications_launcher
prepare_repo https://gitee.com/openharmony/applications_settings
prepare_repo https://gitee.com/openharmony/applications_settings_data
prepare_repo https://gitee.com/openharmony/applications_systemui
