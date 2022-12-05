# Control flow graph implementation for JCDB

Control flow graph (CFG) is a model that represents all paths that can be executed in
the program. JCDB implementation of the CFG is split into two API levels.

## Raw instruction list API

`JcRawInstList` represents "raw" 3-address instruction list representation of the Java
bytecode. "Raw" means that this representation does not resolve any information about
types and control flow of the program. This representation is more-or-less one-to-one
matching of JVM bytecode instructions into the designed list of 3-address instructions.

The base class of this representation is `JcRawInstList`. It represents a list-like
collection of instructions and allows to iterate over the instructions, access them by
index and to modify the list of instructions.

### 3-address instructions

`JcRawInst` is the base interface for the raw instruction. All the instruction are identified
by the object, they are not comparable using `equals`.

List of `JcRawInst` implementations:

* `JcAssignInst` &mdash; Assignment instruction. Left hand side of the instruction can only be a
  `JcRawValue`, right hand side can be any expression (`JcRawExpression`).
* `JcRawEnterMonitorInst`, `JcRawExitMonitorInst` &mdash; Monitor instruction that correspond
  directly to their existing analogs. `monitor` property can only be a `JcRawSimpleValue`.
* `JcRawCallInst` &mdash; Call instruction that represents a method that does not save it's returning
  variable to any local variable. Method calls that return a value represented throug `JcRawAssignInst`.
* `JcRawLabelInst` &mdash; Label instruction, used to mark some program points in the code. Mainly required
  to be used in the branching instructions. Label is identified by a name, all the references
  to a label are represented using `JcRawLabelRef` class.
* `JcRawReturnInst` &mdash; Return instruction, `returnValue` property is null when method does not
  return anything.
* `JcRawThrowInst` &mdash; Throw instruction.
* `JcRawCatchInst` &mdash; Catch instruction that represents an entry for `try-catch` block in the
  code. Does not map directly to bytecode instruction, but represents a `TryCatchBlock` of a method.
  Stores a value that corresponds to a caught exception `throwable` and a range of the instructions
  that in catches from `startInclusive until endExclusive`.
* `JcRawGotoInst` &mdash; Jump instruction.
* `JcRawIfInst` &mdash; Conditional jump instruction. The condition of the instruction should
  necessarily be `JcRawConditionExpr`, because not all the conditional expressions that we use
  in higher-level programming languages can be easily expressed in JVM bytecode.
* `JcRawSwitchInst` &mdash; Switch instruction, combned representation of `LookupSwitch` and `TableSwitch`
  bytecode instructions.

### Raw expressions

`JcRawExpr` is a base interface for all the expression types and value types that can be
expressed in the JVM bytecode. `JcRawExpr` stores its type as a `TypeName` object which is
essentially is just a Java name of the type as string (hence the name "raw"). List of `JcRawExpr` implementations:

* `JcRawBinaryExpr` &mdash; Binary expression, implementations implement all the arithmetic
  expressions (e.g. `JcRawAdd`, `JcRawMul` etc.), conditional expressions (`JcRawEq`, `JcRawGt` etc.),
  logical expressiongs (`JcRawAnd`, `JcRawOr`, `JcRawXor`).
    * `JcRawConditionExpr` &mdash; Conditional expressions, that can be used as a condition in `JcRawIfInst`.
* `JcRawLengthExpr` &mdash; Array length expression.
* `JcRawNegExpr` &mdash; Negation expression.
* `JcRawCastExpr` &mdash; Cast expression. Can be uset to cast both reference types and primitive types.
* `JcRawNewExpr` &mdash; New expression, creates a single object.
* `JcRawNewArrayExpr` &mdash; New array expression, creates a (multi)array of a given type.
* `JcRawInstanceOfExpr` &mdash; Instanceof check.
* `JcRawCallExpr` &mdash; Method call expression.
    * `JcRawDynamicCallExpr` &mdash; `invokedynamic` instruction representation, preserves all the info
    * `JcRawVirtualCallExpr`
    * `JcRawInterfaceCallExpr`
    * `JcRawStaticCallExpr`
    * `JcRawSpecialCallExpr`
* `JcRawValue` &mdash; Representation of a single value:
    * `JcRawSimpleValue` &mdash; Representation of a simple value that does not have any sub-values:
        * `JcRawThis`
        * `JcRawArgument`
        * `JcRawLocal`
        * `JcRawConstant`
    * `JcRawComplexValue` &mdash; Complex value that has a sub-values
        * `JcRawFieldRef` &mdash; Field reference. Can be used both as a field read access (e.g. `a = x.y`)
          and field store access (e.g. `x.y = a`)
        * `JcRawArrayAccess` &mdash; Array element reference. Can be used both as an array read access (e.g. `a = x[y]`)
          and array store access (e.g. `x[y] = a`)

To get an 3-address instruction list representation of a method you need to call `JcMethod::instructionList`.
Instruction list building requires a `JcClasspath`, because some stages require use of subtyping information.

### Some implementation details of instruction list construction

`RawInstListBuilder` is used to build a `JcRawInstList` from bytecode representation (i.e. from `MethodNode`).
To build an instruction list representation, `MethodNode` **should** contain frame information (i.e. `FrameNode`'s)
and **should not** contain any `JSR` (jump subroutine) instructions. Thus, each time a `ClassNode` is
created, we invoke `ClassNode.computeFrames()` extension function that computes frame information
for each method. `computeFrames` function uses ASM functionality for computing frames: converts
the class node back to bytecode using `ClassWriter` (which actually performs frame computing during
the conversion) and reads that bytecode again using `ClassReader`. This is not the most effective way of
doing things, but the easiest one: manually computing frames is quite hard.
Another thing is inlining `JSR` instructions. We do this by calling `MethodNode.jsrInlined` extension that
returns a new `MethodNode` instance. It uses ASM `JSRInlinerAdapter` utility to create new method node with
inlined jsr's.

`RawInstListBuilder` converts JVM bytecode instruction list into a 3-address instruction list. Most of
the coversion process is simple: bytecode instructions match one-to-one to 3-addr expressions and instructions.
The most complex part is frame merging. JVM frame describes the state of the virtual machine at each instruction:
declared local variables and stack state. When an instruction has multiple predecessors we need to merge several
incoming frames into one. Sometimes JVM adds a special frame node before an instruction to describe how the
frame should look like after merging. Frame merging process can be divided into four situations:

* There is only one incoming frame, and it is fully defined (i.e. we already collected all the frame information)
  when converting previous stages. In that case everything is quite simple, we can just copy the frame info.
  However, we can also refine type information for local variables. Consider following bytecode:

```
NEW java/lang/ArrayList
ASTORE 0
...
FRAME FULL [ java/lang/List ]
...
NEW java/lang/LinkedList
ASTORE 0
```

When converting first two instructions, we created a local variable `%0` for cell 0 with type `java.lang.ArrayList`.
However, when converting frame information we found that JVM considers the type of cell 0 to be `java.lang.List`
and then uses cell 0 to store other implementations of Java List. We can refine the type of `%0` to be
`java.lang.List` and then replace all the occurrences of `%0` with the new version. That is performed by
using `localTypeRefinement` property of `RawInstListBuilder` and `ExprMapper` utility class.

* There is only one incoming frame, and it is not yet defined. It is a rare case, but it can happen is
  situation like:

```
GOTO L2
L1
FRAME FULL [ java/lang/List ]
...
GOTO L3
L2
...
GOTO L1
L3
RETURN
```

In this situation we use frame info to create a new local variable with the defined type and remember to
add an assignment that will assign out new variable a correct value in predecessor. It is performed in the
end by using `laterAssignments` and `laterStackAssignments` maps and `buildRequiredAssignments` function. The
process is similar for both stack variables and local variables.

* There are several predecessor frames and all of them are defined (e.g. merge after if - else block). In
  that case we create a new local variable with the defined type in current frame and add necessary assignments
  into the predecessor blocks.

* There are several predecessor frames and not all of them are defined (e.g. merge in the loop header).In
  that case we create a new local variable with the defined type in current frame, add necessary assignments
  into the predecessor blocks, and remember to add the required assignments to undefined predecessor blocks
  in the end. 

`RawInstListBuilder` also performs the simplification of the resulting instruction list. This process is
required because the construction process naturally introduces a lot of redundancy in the instruction list.
The main stages of simplification are:
* Deleting repeated assignments inside a basic block.
* Deleting declarations of unused variables.
* Deleting declarations of mutually dependent unused variables (e.g. `a = b` and `b = a`).
* Simple unit propagation.
* Type normalization using `JcClasspath`.

## Visitor API

We also provide a visitor API for traversing and modifying `JcRawInstList`. Visitors have a standard
interface, they can be invoked using an `accept` method on instructions and expressions:
```
val a = jcRawInst.accept(MyInstVisitor())
val b = jcRawExpr.accept(MyExprVisitor())
```

We also provide a "functional"-like extensions for applying visitors to `JcRawInstList`:
* `filter(visitor: JcRawInstVisitor<Boolean>): JcRawInstList`
* `filterNot(visitor: JcRawInstVisitor<Boolean>): JcRawInstList`
* `map(visitor: JcRawInstVisitor<JcRawInst>): JcRawInstList`
* `mapNotNull(visitor: JcRawInstVisitor<JcRawInst?>): JcRawInstList`
* `flatMap(visitor: JcRawInstVisitor<Collection<JcRawInst>>): JcRawInstList`
* `apply(visitor: JcRawInstVisitor<Unit>): JcRawInstList`
* `applyAndGet(visitor: T, getter: (T) -> R): R`
* `collect(visitor: JcRawInstVisitor<T>): Collection<T>`

`jcdb-core` provides a number of utility visitors for working with the instruction list:
* `ExprMapper(val mapping: Map<JcRawExpr, JcRawExpr>)` &mdash; Traverses an instruction list 
and replaces all occurrences of expressions from `mapping` to the corresponding property.
* `FullExprSetCollector()` &mdash; Collects **all** the expressions that occur in a given object
  (instruction list, single instruction or an expression).
* `InstructionFilter(val predicate: (JcRawInst) -> Boolean)` &mdash; Filters the instructions by
a given predicate.

`JcRawInstList` can be converted back to the ASM `MethodNode` using `MethodNodeBuilder.build()`
method. The conversion process is pretty straightforward and does not require any additional
comments.

# Control flow graph API

A control flow graph of the method is represented as a `JcGraph` object. To create a
`JcGraph` of a method you can invoke `graph` function of a 3-address instruction list:
```kotlin
fun createGraph(classpath: JcClasspath, method: JcMethod): JcGraph {
    val instructionList = method.instructionList(classpath)
    return instructionList.graph(classpath, method)
}
```

## `JcGraph`

Intermediate representation of JcGraph uses the resolved type information (i.e. `JcType` 
hierarchy) and classpath information, therefore it requires a classpath instance.
Similar to `JcRawInstList`, JcGraph stores a list of method instructions. However, it
also tries to resolve all the execution paths in the method. JcGraph operates with
`JcInst` class hierarchy (which is similar to `JcRawInst` in many cases) and provides
following API:
* `entry: JcInst` --- Get the entry point of a method, there can be only one entry point.
* `exits: List<JcInst>` --- Get all the "normal" exit points of a method, i.e. all the return and trow
 instructions.
* `throwExits: Map<JcType, List<JcInst>>` --- All the potential exception exit points of a method.
* `ref(inst: JcInst): JcInstRef` --- Get the `JcInstRef` for an instruction. It is a lightweight wrapper that
allows to reference an instruction anytime you need.
* `inst(ref: JcInstRef): JcInst` --- Convert `JcInstRef` into a `JcInst`.
* `previous(inst: JcInst): JcInst` --- Get the previous instruction in the list.
* `next(inst: JcInst): JcInst` --- Get the next instruction in the list.
* `successors(inst: JcInst): Set<JcInst>` --- Get all the successors of an instruction
in a CFG. **Does not include any exception control flow**.
* `predecessors(inst: JcInst): Set<JcInst>` --- Get all the predecessors of an instruction
  in a CFG. **Does not include any exception control flow**.
* `throwers(inst: JcInst): Set<JcInst>` --- Get all the instructions that may throw an
exception that is caught by `inst`. Represents an exception control flow of
a method. Will return an empty set for all instructions except `JcCatchInst`.
* `catchers(inst: JcInst): Set<JcCatchInst>` --- Get all the instructions that may catch an
  exception that is thrown by `inst`. Represents an exception control flow of
  a method.
* `exceptionExits(inst: JcInst): Set<JcClassType>` --- Get all the exception types that
an instruction can throw and method will not catch.
* `blockGraph(): JcBlockGraph` --- Create a basic block representation of a CFG.
* `iterator(): Iterator<JcInst>` --- Iterator over the instructions of a graph.

## `JcBlockGraph`

`JcBlockGraph` is a basic block API for CFG. It operates with `JcBasicBlock`'s, each
basic block jst represents an interval of instructions with following properties:
* Instructions of a basic block are executed consecutively one after other during
the normal execution (i.e. no exceptions are thrown).
* All the instructions of a basic block have the same exception handlers, i.e. calling
`jcGraph.catchers(inst)` for each instruction of a basic block will return the same result.

`JcBlockGraph` provides following API:
* `entry: JcBasicBlock` --- Entry of a method. There can be only one entry.
* `exits: List<JcBasicBlock>` --- Exits of a method.
* `instructions(block: JcBasicBlock): List<JcInst>` --- Get the instructions of a basic
block.
* `predecessors(block: JcBasicBlock): Set<JcBasicBlock>` --- Get all the predecessors of a
basic block in a CFG. **Does not include any exception control flow**.
* `successors(block: JcBasicBlock): Set<JcBasicBlock>` --- Get all the successors of a
  basic block in a CFG. **Does not include any exception control flow**.
* `throwers(block: JcBasicBlock): Set<JcBasicBlock>` --- Get all the basic blocks that may throw an
  exception that is caught by `block`. Represents an exception control flow of
  a method. Will return an empty set for all blocks except ones that start with `JcCatchInst`.
* `catchers(block: JcBasicBlock): Set<JcBasicBlock>` --- Get all the basic blocks that may catch an
  exception that is thrown by `block`. Represents an exception control flow of
  a method.

We also provide an API to visualize the `JcGraph` and `JcBlockGraph`:
* `JcGraph.view(dotCmd: String, viewerCmd: String, viewCatchConnections: Boolean = false)` ---
Generate a svg file using DOT ('dotCmd' requires a path to executable of a DOT) and view
it using `viewerCmd` program (e.g. executable of a browser). `viewCatchConnections` flag
defines whether a throw-catch connections will be displayed in the graph.
* `JcBlockGraph.view(dotCmd: String, viewerCmd: String)` --- Similar, but it displays
`JcBlockGraph`.

CFG API operates on `JcInst` instructions. `JcInst` is similar to `JcRawInst` with some
small differences. The main difference is that `JcInst` uses `JcType`'s to represent
types. Another difference is that `JcInst` does not need tha labels to represent connections
between instructions (as they are stored in `JcGraph`). In all other cases `JcInst` hierarchy
(including `JcExpr` and `JcValue`) are almost one-to-one matched with `JcRawInst` hierarchy
(including `JcRawExpr` and `JcRawValue`).

One more thing worth noting is that `JcGraph` represent an immutable structure and does
not provide any API for its modification. It is so on purpose, because modifying CFG
requires awareness of all the connections inside a graph and user should correctly manage
those connections when changing the CFG. However, user can always create a new copy of
a `JcGraph` with all the necessary modifications. 

## Examples

An example of CFG modification is given in `StringConcatSimplifier` class: it creates a new
`JcGraph` in which all the `invokedynamic` string concatenation instructions are 
replaced with simple `String.concat` method call.

`ReachingDefinitionsAnalysis` class is an example of using basic block API. It performs
a standard reaching definitions analysis for basic blocks using simple worklist algorithm.

## Visitor API

As with 3-address instruction list, we also provide a visitor API for traversing and
modifying `JcGraph`. Visitors have a standard interface, they can be invoked using an 
`accept` method on instructions and expressions:
```
val a = jcInst.accept(MyInstVisitor())
val b = jcExpr.accept(MyExprVisitor())
```

We also provide a "functional"-like extensions for applying visitors to `JcGraph`:
* `filter(visitor: JcInstVisitor<Boolean>): JcGraph`
* `filterNot(visitor: JcInstVisitor<Boolean>): JcGraph`
* `map(visitor: JcInstVisitor<JcInst>): JcGraph`
* `mapNotNull(visitor: JcInstVisitor<JcInst?>): JcGraph`
* `flatMap(visitor: JcInstVisitor<Collection<JcInst>>): JcGraph`
* `apply(visitor: JcInstVisitor<Unit>): JcGraph`
* `applyAndGet(visitor: T, getter: (T) -> R): R`
* `collect(visitor: JcInstVisitor<T>): Collection<T>`
