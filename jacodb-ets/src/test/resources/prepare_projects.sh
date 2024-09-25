#/bin/bash
set -euo pipefail

if [[ -z "${ARKANALYZER_DIR}" ]]; then
  echo "ARKANALYZER_DIR is undefined"
  exit 1
fi

echo "ARKANALYZER_DIR=${ARKANALYZER_DIR}"
SCRIPT=$ARKANALYZER_DIR/src/save/serializeArkIR.ts

pushd "$(dirname $0)" >/dev/null
mkdir -p projects
pushd projects

function check_repo() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: check_repo <repo>"
    exit 1
  fi
  if [[ ! -d $1 ]]; then
    echo "Repository not found: $1"
    exit 1
  fi
}

function prepare_module() {
  if [[ $# -ne 2 ]]; then
    echo "Usage: prepare_module <module> <root>"
    exit 1
  fi
  local MODULE=$1
  local ROOT=$2
  echo "= Preparing module: $MODULE"
  local SRC="source/$MODULE"
  local ETSIR="etsir/$MODULE"
  mkdir -p $(dirname $SRC)
  echo "Linking sources..."
  echo "pwd = $(pwd)"
  ln -srfT "$ROOT/src/main/ets" $SRC
  echo "Serializing..."
  npx ts-node $SCRIPT -p $SRC $ETSIR -v
}

(
  NAME="ArkTSDistributedCalc"
  echo
  echo "=== Preparing $NAME..."
  echo
  if [ -d $NAME ]; then
    echo "Directory already exists: $NAME"
    exit
  fi
  mkdir -p $NAME
  pushd $NAME

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  prepare_module "entry" "$REPO/code/SuperFeature/DistributedAppDev/ArkTSDistributedCalc/entry"
)

(
  NAME="AudioPicker"
  echo
  echo "=== Preparing $NAME..."
  echo
  if [[ -d $NAME ]]; then
    echo "Directory already exists: $NAME"
    exit
  fi
  mkdir -p $NAME
  pushd $NAME

  REPO="../../repos/applications_filepicker"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
  prepare_module "audiopicker" "$REPO/audiopicker"
)

(
  NAME="Launcher"
  echo
  echo "=== Preparing $NAME..."
  echo
  if [[ -d $NAME ]]; then
    echo "Directory already exists: $NAME"
    exit
  fi
  mkdir -p $NAME
  pushd $NAME

  REPO="../../repos/applications_launcher"
  check_repo $REPO

  prepare_module "common" "$REPO/common"
  prepare_module "phone_launcher" "$REPO/product/phone"
)
