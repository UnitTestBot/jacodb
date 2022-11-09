# Design draft for JCDB control flow graph implementation

## features

* two separate IR's:
    1. "Raw" 3-address instructions list. Does not use type resolving or anything, just
       plain conversion of bytecode to 3-addr instructions.
  2. "CFG"
* IR for modification, transformation and analysis with 3-addr instructions
    * 3-addr instructions are easier for analysis, transformation and construction
* CFG for JVM bytecode built on top of ASM representation
    * preferably without needing to resolve all the classpath for IR construction
        * does not allow to resolve all method exits: normal ones and exception exits
        * less information about potential paths in method
    * CFG is built on request and is not stored anywhere in the system
        * caching should be implemented on the user side
    * effective serialization/deserialization for CFG is required, even if we do not
      plan to store it in the database
* bidirectional transformation: bytecode -> IR -> bytecode
    * allows to modify the classes during runtime and use the IR to construct new classes
* instructions are CFG nodes
    * the idea is that basic block level representation is unnecessary in many cases
    * exception paths are stored in some way in cfg
        * special `Catch` instruction is introduced to explicitly distinguish the
          entry points of the catch blocks in a method
            * if this instruction is not introduced, then each instruction will have to store
              potential exception catching paths, and we will not be able to distinguish this kind of
              instructions
        * either each instruction has an implicit edge to the exception receiver (either
          catch or method exit)
            * more complex API, harder to keep track of. Especially when constructing
              completely new methods
        * or instructions do not store exception path information, it is only stored
          in catch instructions (each catch stores a list of throwing instructions)
            * need to manually resolve all the information when it is needed
            * harder to determine connections of each separate instruction
            * all resolving can be done later, after the resolving stage, with special
              util functions
    * instructions store their mapping to the bytecode
        * don't know why
    * instructions are immutable
* basic block API
    * even if it is unnecessary, it is good to have it if we can make it 'free'
    * basic blocks are just 'views' that are mapped to the range of instructions
      in the original cfg
    * exception paths are represented in basic block API similarly to instructions
        * need to decide how to distinguish between normal basic blocks and exception
          handling basic blocks, should we add explicit `CatchBlocks` to the API or not
        * my proposal:
            * basic blocks represents a continuous set of instructions in the CFG that are
              **guaranteed** to be executed one after other
            * that means, that basic block end either with branching instruction, or with
              exception-throwing instruction
            * similarly to the source code, usual basic blocks store normal execution paths
              and do not store exception paths implicitly **or** explicitly
            * special 'CatchBlocks' are introduced to represent catch blocks of a method,
              Catch block is guaranteed to start with 'Catch' instruction and, similarly to it,
              stores all the information about potential exception-throwing blocks that it catches
              from
            * downside &mdash; it is harder to sync the basic blocks with the instructions,
              especially when the CFG is modified
* interop with java is necessary

## lower priority features

* graph visualization
    * export to dot format at first, high priority for debug purposes
    * better options (e.g. interactive view in browser etc.) later
* 'dsl' for runtime CFG construction
    * including builders for more effective CFG construction
    * example
      ```kotlin
      buildCfg {
      val a = param(IntType)
      val b = param(IntType)
      `if`(a > b)
          .goto(label("l1"))
          .`else`(label("l2"))
      label("l1")
      `return`(a)
      label("l2")
      `return`(b)
      }
      ```
* API for IR modification
    * basic 'visitor' implementation
    * easy to use interface for combining multiple transformations:
      ```kotlin
      method
          .apply(StringConcatFix())
          .apply(SomeOtherTransformation())
      ```
* built-in IR transformations (like replacing `invokedynamic` for `String.concat`)
    * need to think about other options

### potential ideas

* split `calls` into two separate instructions
    * simplifies analysis for symbolic execution engines, complicates the workflow
      for many other applications of jcdb
* option to add phantom nodes or to extend API with custom node types
    * gives users opportunity to extend the CFG with various kinds of nodes in the CFG,
      e.g. implement the `call` instruction splitting customly. Potentially breaks the
      API, increases the chance of error occurrence

## analogues

* [Soot](https://github.com/soot-oss/soot) and Jimple
    * 'reference' implementation
    * bad licence

## API design draft

```kotlin
/**
 * class that represents CFG of a method
 */
class JcGraph {
    /**
     * internally I think this will be represented just as an ArrayList
     * there is no need to store it as basic array
     */
    val instructions: List<JcInst>

    /**
     * previous instruction in the raw instruction list
     */
    fun previous(jcInst: JcInst): JcInst

    /**
     * next instruction in the raw instruction list
     */
    fun next(jcInst: JcInst): JcInst

    /**
     * incoming edges in the control flow of a method
     */
    fun predecessors(jcInst: JcInst): List<JcInst>

    /**
     * outgoing edges in the control flow of a method
     */
    fun successors(jcInst: JcInst): List<JcInst>

    /**
     * implicit incoming edges, that indicate control
     * flow during exception occurrence, needed to identify
     */
    fun implicitPredecessors(): List<JcExceptionReceiver>

    /**
     * implicit outgoing edges, that indicate control
     * flow during exception occurrence
     */
    fun implicitSuccessors(): List<JcExceptionReceiver>

    /**
     * simple way to access all entries/exits of a graph
     */
    val instructionEntry: JcInst
    val instructionExits: List<JcInst>
    val exceptionExits: List<JcExceptionReceiver>

    /**
     * basic blocks of a graph
     * computed lazily once for each graph
     */
    val basicBlocks: List<JcBasicBlock>

    /**
     * instructions that belong to a giver block
     * boundaries of blocks are stored in the JcGraph to simplify
     * the process of their synchronization when graph is modified
     */
    fun instructions(basicBlock: JcBasicBlock): List<JcInst>

    // todo
    // also we need to provide entry/exit access for
    // basic block API, but we need to decide about handling exceptions
    // in basic blocks first
}

class JcBasicBlock {
    /**
     * incoming edges of a control flow graph
     */
    val predecessors: List<JcBasicBlock>

    /**
     * outgoing edges of a control flow graph
     */
    val successors: List<JcBasicBlock>

    // todo 
    // need to think about exceptions in the basic
    // block API. Should we represent exception edges
    // as implicit edges, or explicit edges? Should we
    // explicitly distinguish catch blocks from the
    // usual blocks?
}

/**
 * represents an exception receiver in a method. It can be
 * either a JcCatchInst (if there is a catch instruction in a method
 * that handles thrown exception) or a synthetic exit node in a graph
 */
interface JcExceptionReceiver

/**
 * represents an instruction of control flow graph
 */
sealed class JcInst {// interface?
    // API
    /**
     * simple way to get *all* the JcExpressions of an instruction
     * sometimes it is convenient, need to think do we need it or not
     */
    val operands: List<JcExpr>

    abstract fun clone(): JcInst

    // internal function to keep all the indices inside the instruction
    // synced with the JcGraph in case it has been changed
    // mainly required for jump instructions
    internal open fun updateIndices(start: Int, offset: Int) {}
}

// JcInst implementations
class JcAssignInst : JcInst()
class JcEnterMonitorInst : JcInst()
class JcExitMonitorInst : JcInst()
class JcCallInst : JcInst()
class JcCatchInst : JcInst()
class JcGotoInst : JcInst()
class JcIfInst : JcInst()

// todo
// do we need this kind of instruction for declaring variables of a method?
// added this for more similarity with Jimple
class JcIdentityInst : JcInst()

class JcNopInst : JcInst()
class JcReturnInst : JcInst()
class JcSwitchInst : JcInst()

/**
 * represents an expression used in the JcInst
 */
sealed class JcExpr { // interface?
    /**
     * simple way to get *all* the JcExpressions of an instruction
     * sometimes it is convenient, need to think do we need it or not
     */
    val operands: List<JcValue>
}

// JcExpr implementations
class JcAddExpr : JcExpr()
class JcAndExpr : JcAnd()

// ... all binary operations
class JcCastExpr : JcCastExpr()
class JcCmpExpr : JcExpr()
class JcCmpgExpr : JcExpr()
class JcCmplExpr : JcExpr()
class JcEqExpr : JcExpr()

// ... all binary cmp operations
class JcLengthExpr : JcExpr()
class JcNegExpr : JcExpr()
class JcNewExpr : JcExpr()
class JcNewArrayExpr : JcExpr()
class JcInstanceOfExpr : JcExpr()
class JcDynamicCallExpr : JcExpr()
class JcVirtualCallExpr : JcExpr()
class JcStaticCallExpr : JcExpr()
class JcSpecialCallExpr : JcExpr()

/**
 * represents a single value
 */
sealed class JcValue

// JcValue implementations
sealed class JcConstant : JcValue() // interface?
class JcBoolConstant : JcConstant()
class JcByteConstant : JcConstant()

// ... all primitive constants
class JcStringConstant : JcConstant()
class JcClassConstant : JcConstant()
class JcMethodHandle : JcConstant()
class JcNullConstant : JcConstant()

class JcThisRef : JcValue()
class JcArgument : JcValue()
class JcLocalVariable : JcValue()
```
