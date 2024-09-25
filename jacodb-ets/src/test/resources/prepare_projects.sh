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
  if [[ ! -d $1 ]]; then
    echo "Repository not found: $1"
    exit 1
  fi
}

function prepare_app_samples() {
  NAME="AppSamples"
  echo
  echo "=== Preparing $NAME..."
  echo
  if [ -d $NAME ]; then
    echo "Directory already exists: $NAME"
    return
  fi
  mkdir -p $NAME
  pushd $NAME >/dev/null

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  function prepare_calc() {
    NAME="ArkTSDistributedCalc"
    echo "= Preparing subproject: $NAME"
    mkdir -p $NAME
    SRC="$NAME/source"
    ETSIR="$NAME/etsir"
    echo "Linking sources..."
    ln -srfT "$REPO/code/SuperFeature/DistributedAppDev/$NAME/entry/src/main/ets" $SRC
    echo "Serializing..."
    npx ts-node $SCRIPT -p $SRC $ETSIR -v
  }

  prepare_calc
}

prepare_app_samples

function prepare_audiopicker() {
  NAME="AudioPicker"
  echo
  echo "=== Preparing $NAME..."
  echo
  if [ -d $NAME ]; then
    echo "Directory already exists: $NAME"
    return
  fi
  mkdir -p $NAME
  pushd $NAME >/dev/null

  REPO="../../repos/applications_filepicker"
  check_repo $REPO

  SRC="source"
  ETSIR="etsir"
  echo "Linking sources..."
  ln -srfT "$REPO/audiopicker/src/main/ets" $SRC
  echo "Serializing..."
  npx ts-node $SCRIPT -p $SRC $ETSIR -v
}

prepare_audiopicker
