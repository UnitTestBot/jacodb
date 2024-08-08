enum SceneBuildStage {
  BUILD_INIT = 1 << 1,
  CLASS_DONE = 1 << 2,
  METHOD_DONE = BUILD_INIT | CLASS_DONE,
  ALL = 'all'.length,
}

const ALL_BUILD_STAGE = [
  SceneBuildStage.BUILD_INIT,
  SceneBuildStage.CLASS_DONE,
  SceneBuildStage.METHOD_DONE,
];

export enum ValueTag {
  TAINT,
}

export enum ExportType {
  NAME_SPACE = 0,
  CLASS = 1,
  METHOD = 2,
  LOCAL = 3,
  UNKNOWN = 4,
}

declare enum ViewTreeNodeType {
  SystemComponent,
  CustomComponent,
  Builder,
  BuilderParam,
}

let systemComponent = ViewTreeNodeType.SystemComponent;
let nameOfsystemComponent = ViewTreeNodeType[systemComponent];

let obj: Object = { x: 1 };
for (const [key, value] of Object.entries(ViewTreeNodeType)) {
  obj[key] = value;
}

if (!obj.hasOwnProperty('SystemComponent')) {
  console.log('error');
}

delete obj['x'];
