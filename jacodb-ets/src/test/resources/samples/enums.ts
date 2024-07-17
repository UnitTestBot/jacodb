let $temp0: any;
let c: Circle;
let a2: any;
let nameOfA: any;
let directions: (any)[];
let $temp1: any;
let $temp2: any;
let $temp3: any;
let $temp4: any;
let $temp5: (any)[];

$temp0 = Response1.Yes;
respond('Princess Caroline',$temp0);
c = @save/enums: AnonymousClass-enums.ts-1;
f(E);
a2 = Enum.A;
nameOfA = Enum.a2;
$temp1 = Directions.Up;
$temp2 = Directions.Down;
$temp3 = Directions.Left;
$temp4 = Directions.Right;
$temp5 = new Array<any>(1);
directions = $temp5;
return;
enum Direction1{
  Up,
  Down,
  Left,
  Right,
}
enum Direction2{
  Up,
  Down,
  Left,
  Right,
}
enum BooleanLikeHeterogeneousEnum{
  No,
  Yes,
}
enum E1{
  X,
  Y,
  Z,
}
enum E2{
  A,
  B,
  C,
}
enum FileAccess{
  None,
  Read,
  Write,
  ReadWrite,
  G,
}
enum Response1{
  No,
  Yes,
}
function respond(recipient: string,message: Response1): void {
  
  
  
  return;
}
enum ShapeKind{
  Circle,
  Square,
}
interface Circle{
  kind:ShapeKind.Circle;
  radius:number;
}
interface Square{
  kind:ShapeKind.Square;
  sideLength:number;
}
object AnonymousClass-enums.ts-1{
  kind;
  radius;
}
enum E{
  X,
  Y,
  Z,
}
function f(obj: any){
  let $temp0: any;
  
  
  $temp0 = obj.X;
  return $temp0;
}
declare enum Enum{
  A,
}
const enum Directions{
  Up,
  Down,
  Left,
  Right,
}
