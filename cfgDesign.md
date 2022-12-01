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

`JcRawInst` is the base interface for the raw instruction. All the instruction are identified
by the object, they are not comparable using `equals`.

List of `JcRawInst` implementations:

* `JcAssignInst` --- Assignment instruction. Left hand side of the instruction can only be a
  `JcRawValue`, right hand side can be any expression (`JcRawExpression`).
* `JcRawEnterMonitorInst`, `JcRawExitMonitorInst` --- Monitor instruction that correspond
  directly to their existing analogs. `monitor` property can only be a `JcRawSimpleValue`.
* `JcRawCallInst` --- Call instruction that represents a method that does not save it's returning
  variable to any local variable. Method calls that return a value represented throug `JcRawAssignInst`.
* `JcRawLabelInst` --- Label instruction, used to mark some program points in the code. Mainly required
  to be used in the branching instructions. Label is identified by a name, all the references
  to a label are represented using `JcRawLabelRef` class.
* `JcRawReturnInst` --- Return instruction, `returnValue` property is null when method does not
  return anything.
* `JcRawThrowInst` --- Throw instruction.
* `JcRawCatchInst` --- Catch instruction that represents an entry for `try-catch` block in the
  code. Does not map directly to bytecode instruction, but represents a `TryCatchBlock` of a method.
  Stores a value that corresponds to a caught exception `throwable` and a range of the instructions
  that in catches from `startInclusive until endExclusive`.
* `JcRawGotoInst` --- Jump instruction.
* `JcRawIfInst` --- Conditional jump instruction. The condition of the instruction should
  necessarily be `JcRawConditionExpr`, because not all the conditional expressions that we use
  in higher-level programming languages can be easily expressed in JVM bytecode.
* `JcRawSwitchInst` --- Switch instruction, combned representation of `LookupSwitch` and `TableSwitch`
  bytecode instructions.

`JcRawExpr` is a base interface for all the expression types and value types that can be
expressed in the JVM bytecode. List of `JcRawExpr` implementations:

* `JcRawBinaryExpr` --- Binary expression, implementations implement all the arithmetic
  expressions (e.g. `JcRawAdd`, `JcRawMul` etc.), conditional expressions (`JcRawEq`, `JcRawGt` etc.),
  logical expressiongs (`JcRawAnd`, `JcRawOr`, `JcRawXor`).
    * `JcRawConditionExpr` --- Conditional expressions, that can be used as a condition in `JcRawIfInst`.
* `JcRawLengthExpr` --- Array length expression.
* `JcRawNegExpr` --- Negation expression.
* `JcRawCastExpr` --- Cast expression. Can be uset to cast both reference types and primitive types.
* `JcRawNewExpr` --- New expression, creates a single object.
* `JcRawNewArrayExpr` --- New array expression, creates a (multi)array of a given type.
* `JcRawInstanceOfExpr` --- Instanceof check.
* `JcRawCallExpr` --- Method call expression.
    * `JcRawDynamicCallExpr` --- `invokedynamic` instruction representation, preserves all the info
    * `JcRawVirtualCallExpr`
    * `JcRawInterfaceCallExpr`
    * `JcRawStaticCallExpr`
    * `JcRawSpecialCallExpr`
* `JcRawValue` --- Representation of a single value:
    * `JcRawSimpleValue` --- Representation of a simple value that does not have any sub-values:
        * `JcRawThis`
        * `JcRawArgument`
        * `JcRawLocal`
        * `JcRawConstant`
    * `JcRawComplexValue` --- Complex value that has a sub-values
        * `JcRawFieldRef` --- Field reference. Can be used both as a field read access (e.g. `a = x.y`)
          and field store access (e.g. `x.y = a`)
        * `JcRawArrayAccess` --- Array element reference. Can be used both as an array read access (e.g. `a = x[y]`)
          and array store access (e.g. `x[y] = a`)

To get an 3-address instruction list representation of a method you need to call `JcMethod::instructionList`.
Instruction list building requires a `JcClasspath`, because some stages require use of subtyping information.

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
* deleting repeated assignments inside a basic block
* deleting declarations of unused variables
* deleting declarations of mutually dependent unused variables (e.g. `a = b` and `b = a`)
* simple unit propagation
* type normalization using `JcClasspath`
