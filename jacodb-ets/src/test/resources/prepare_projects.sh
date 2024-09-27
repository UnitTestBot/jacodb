#/bin/bash
set -euo pipefail

if [[ -z "${ARKANALYZER_DIR}" ]]; then
  echo "ARKANALYZER_DIR is undefined"
  exit 1
fi

echo "ARKANALYZER_DIR=${ARKANALYZER_DIR}"
SCRIPT_TS=$ARKANALYZER_DIR/src/save/serializeArkIR.ts
SCRIPT_JS=$ARKANALYZER_DIR/out/src/save/serializeArkIR.js

if [[ ! -f $SCRIPT_JS ]]; then
  echo "Script not found: $SCRIPT_JS"
  echo "Did you forget to build the ArkAnalyzer project?"
  echo "Run 'npm run build' in the ArkAnalyzer project directory"
  exit 1
fi

#if [[ $SCRIPT_JS -ot $SCRIPT_TS ]]; then
#  echo "Script is outdated: $SCRIPT_JS"
#  echo "Did you forget to re-build the ArkAnalyzer project?"
#  echo "Run 'npm run build' in the ArkAnalyzer project directory"
#  exit 1
#fi

do_force=0

while getopts ":f" opt; do
  case $opt in
   f) do_force=1
      echo "Force mode enabled"
      ;;
   *) printf "Illegal option '-%s'\n" "$opt" && exit 1
      ;;
  esac
done

cd "$(dirname $0)"
mkdir -p projects
cd projects

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

function prepare_project_dir() {
  if [[ $# -ne 1 ]]; then
    echo "Usage: prepare_project <name>"
    exit 1
  fi
  NAME=$1
  echo
  echo "=== Preparing project: $NAME"
  echo
  if [[ -d $NAME ]]; then
    echo "Directory already exists: $NAME"
    # If `-f` (force mode) is not provided, exit the preparation for this project:
    if [[ $do_force -eq 0 ]]; then
      exit
    fi
  fi
  mkdir -p $NAME
  cd $NAME
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
  # TODO: add switch for using npx/node
  # npx ts-node --files --transpileOnly $SCRIPT_TS -p $SRC $ETSIR -v
  node $SCRIPT_JS -p $SRC $ETSIR -v
}

(
  prepare_project_dir "ArkTSDistributedCalc"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  prepare_module "entry" "$REPO/code/SuperFeature/DistributedAppDev/ArkTSDistributedCalc/entry"
)

(
  prepare_project_dir "AudioPicker"

  REPO="../../repos/applications_filepicker"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
  prepare_module "audiopicker" "$REPO/audiopicker"
)

(
  prepare_project_dir "Launcher"

  REPO="../../repos/applications_launcher"
  check_repo $REPO

  prepare_module "launcher_common" "$REPO/common"
  prepare_module "launcher_appcenter" "$REPO/feature/appcenter"
  prepare_module "launcher_bigfolder" "$REPO/feature/bigfolder"
  prepare_module "launcher_form" "$REPO/feature/form"
  prepare_module "launcher_gesturenavigation" "$REPO/feature/gesturenavigation"
  prepare_module "launcher_numbadge" "$REPO/feature/numbadge"
  prepare_module "launcher_pagedesktop" "$REPO/feature/pagedesktop"
  prepare_module "launcher_recents" "$REPO/feature/recents"
  prepare_module "launcher_smartDock" "$REPO/feature/smartdock"
  prepare_module "phone_launcher" "$REPO/product/phone"
  prepare_module "pad_launcher" "$REPO/product/pad"
  prepare_module "launcher_settings" "$REPO/feature/settings"
)

(
  prepare_project_dir "Settings"

  REPO="../../repos/applications_settings"
  check_repo $REPO

  prepare_module "phone" "$REPO/product/phone"
  # prepare_module "wearable" "$REPO/product/wearable"
  prepare_module "component" "$REPO/common/component"
  prepare_module "search" "$REPO/common/search"
  ### prepare_module "settingsBase" "$REPO/common/settingsBase"
  prepare_module "utils" "$REPO/common/utils"
)

(
  prepare_project_dir "SettingsData"

  REPO="../../repos/applications_settings_data"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
)
