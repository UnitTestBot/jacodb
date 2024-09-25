#/bin/bash
set -euo pipefail

if [[ -z "${ARKANALYZER_DIR}" ]]; then
  echo "ARKANALYZER_DIR is undefined"
  exit 1
fi

echo "ARKANALYZER_DIR=${ARKANALYZER_DIR}"
SCRIPT=$ARKANALYZER_DIR/src/save/serializeArkIR.ts

pushd "$(dirname $0)/projects"

function prepare_app_samples() {
  echo
  echo "=== Preparing AppSamples..."
  echo
  mkdir -p AppSamples
  pushd AppSamples

  REPO="../../repos/applications_app_samples"
  if [[ ! -d $REPO ]]; then
    echo "Repository not found: $REPO"
    exit 1
  fi

  function prepare_calc() {
    NAME="ArkTSDistributedCalc"
    echo "= Preparing subproject: $NAME"
    mkdir -p $NAME
    SRC="$NAME/source"
    ETSIR="$NAME/etsir"
    ln -srfT "$REPO/code/SuperFeature/DistributedAppDev/$NAME/entry/src/main/ets" $SRC
    npx ts-node $SCRIPT -p $SRC $ETSIR -v
  }

  prepare_calc
}

prepare_app_samples
