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
  prepare_project_dir "Demo_Calc"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/SuperFeature/DistributedAppDev/ArkTSDistributedCalc"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_Camera"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/BasicFeature/Media/Camera"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_CertificateManager"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/BasicFeature/Security/CertManager"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_Clock"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/Solutions/Tools/ArkTSClock"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_KikaInput"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/Solutions/InputMethod/KikaInput"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_Launcher"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/SystemFeature/ApplicationModels/Launcher"
  prepare_module "entry" "$BASE/entry"
  prepare_module "desktop" "$BASE/desktop"
  prepare_module "base" "$BASE/base"
  prepare_module "recents" "$BASE/recents"
)
(
  prepare_project_dir "Demo_Music"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/SuperFeature/DistributedAppDev/ArkTSDistributedMusicPlayer"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_Photos"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/SystemFeature/FileManagement/Photos"
  prepare_module "entry" "$BASE/entry"
)
(
  prepare_project_dir "Demo_ScreenShot"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/SystemFeature/Media/Screenshot"
  prepare_module "entry" "$BASE/entry"
  prepare_module "Feature" "$BASE/Feature"
)
(
  prepare_project_dir "Demo_Settings"

  REPO="../../repos/applications_app_samples"
  check_repo $REPO

  BASE="$REPO/code/SuperFeature/MultiDeviceAppDev/Settings"
  prepare_module "default" "$BASE/products/default"
  prepare_module "common" "$BASE/common"
  prepare_module "settingItems" "$BASE/features/settingitems"
)

(
  prepare_project_dir "CalendarData"

  REPO="../../repos/applications_calendar_data"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
  prepare_module "common" "$REPO/common"
  prepare_module "datastructure" "$REPO/datastructure"
  prepare_module "datamanager" "$REPO/datamanager"
  prepare_module "rrule" "$REPO/rrule"
  prepare_module "dataprovider" "$REPO/dataprovider"
)

(
  prepare_project_dir "CallUI"

  REPO="../../repos/applications_call"
  check_repo $REPO

  prepare_module "callui" "$REPO/entry"
  prepare_module "common" "$REPO/common"
  prepare_module "mobiledatasettings" "$REPO/mobiledatasettings"
)

(
  prepare_project_dir "Contacts"

  REPO="../../repos/applications_contacts"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
  prepare_module "common" "$REPO/common"
  prepare_module "phonenumber" "$REPO/feature/phonenumber"
  prepare_module "contact" "$REPO/feature/contact"
  prepare_module "account" "$REPO/feature/account"
  prepare_module "call" "$REPO/feature/call"
  prepare_module "dialpad" "$REPO/feature/dialpad"
)

(
  prepare_project_dir "FilePicker"

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
  prepare_project_dir "Mms"

  REPO="../../repos/applications_mms"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
)

(
  prepare_project_dir "Note"

  REPO="../../repos/applications_notes"
  check_repo $REPO

  prepare_module "default" "$REPO/product/default"
  prepare_module "utils" "$REPO/common/utils"
  # prepare_module "resources" "$REPO/common/resources"
  prepare_module "component" "$REPO/features"
)

(
  prepare_project_dir "PrintSpooler"

  REPO="../../repos/applications_print_spooler"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
  prepare_module "common" "$REPO/common"
  prepare_module "ippPrint" "$REPO/feature/ippPrint"
)

(
  prepare_project_dir "ScreenLock"

  REPO="../../repos/applications_screenlock"
  check_repo $REPO

  prepare_module "entry" "$REPO/entry"
  prepare_module "pc" "$REPO/product/pc"
  prepare_module "phone" "$REPO/product/phone"
  prepare_module "batterycomponent" "$REPO/features/batterycomponent"
  prepare_module "clockcomponent" "$REPO/features/clockcomponent"
  prepare_module "datetimecomponent" "$REPO/features/datetimecomponent"
  prepare_module "noticeitem" "$REPO/features/noticeitem"
  prepare_module "screenlock" "$REPO/features/screenlock"
  prepare_module "shortcutcomponent" "$REPO/features/shortcutcomponent"
  prepare_module "signalcomponent" "$REPO/features/signalcomponent"
  prepare_module "wallpapercomponent" "$REPO/features/wallpapercomponent"
  prepare_module "wificomponent" "$REPO/features/wificomponent"
  prepare_module "common" "$REPO/common"
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

(
  prepare_project_dir "SystemUI"

  REPO="../../repos/applications_systemui"
  check_repo $REPO

  prepare_module "phone_entry" "$REPO/entry/phone"
  prepare_module "pc_entry" "$REPO/entry/pc"
  prepare_module "default_navigationBar" "$REPO/product/default/navigationBar"
  prepare_module "default_notificationmanagement" "$REPO/product/default/notificationmanagement"
  prepare_module "default_volumepanel" "$REPO/product/default/volumepanel"
  prepare_module "default_dialog" "$REPO/product/default/dialog"
  prepare_module "pc_controlpanel" "$REPO/product/pc/controlpanel"
  prepare_module "pc_notificationpanel" "$REPO/product/pc/notificationpanel"
  prepare_module "pc_statusbar" "$REPO/product/pc/statusbar"
  prepare_module "phone_dropdownpanel" "$REPO/product/phone/dropdownpanel"
  prepare_module "phone_statusbar" "$REPO/product/phone/statusbar"
  prepare_module "common" "$REPO/common"
  prepare_module "airplanecomponent" "$REPO/features/airplanecomponent"
  prepare_module "autorotatecomponent" "$REPO/features/autorotatecomponent"
  prepare_module "batterycomponent" "$REPO/features/batterycomponent"
  prepare_module "bluetoothcomponent" "$REPO/features/bluetoothcomponent"
  prepare_module "brightnesscomponent" "$REPO/features/brightnesscomponent"
  prepare_module "capsulecomponent" "$REPO/features/capsulecomponent"
  prepare_module "clockcomponent" "$REPO/features/clockcomponent"
  prepare_module "controlcentercomponent" "$REPO/features/controlcentercomponent"
  prepare_module "locationcomponent" "$REPO/features/locationcomponent"
  prepare_module "managementcomponent" "$REPO/features/managementcomponent"
  prepare_module "navigationservice" "$REPO/features/navigationservice"
  prepare_module "nfccomponent" "$REPO/features/nfccomponent"
  prepare_module "noticeitem" "$REPO/features/noticeitem"
  prepare_module "ringmodecomponent" "$REPO/features/ringmodecomponent"
  prepare_module "signalcomponent" "$REPO/features/signalcomponent"
  prepare_module "statusbarcomponent" "$REPO/features/statusbarcomponent"
  prepare_module "volumecomponent" "$REPO/features/volumecomponent"
  prepare_module "volumepanelcomponent" "$REPO/features/volumepanelcomponent"
  prepare_module "wificomponent" "$REPO/features/wificomponent"
)
