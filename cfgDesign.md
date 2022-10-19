# Design draft for JCDB control flow graph implementation

### features

* IR for modification, transformation and analysis with 3-addr instructions
  * 3-addr instructions are easier for analysis, transformation and construction
* CFG for JVM bytecode built on top of ASM representation
* bidirectional transformation: bytecode -> IR -> bytecode
  * allows to modify the classes during runtime and use the IR to construct new classes
* instructions are CFG nodes
  * exception paths are stores in some way in cfg
    * each instruction has an implicit edge to the exception receiver (either
    catch or method exit)
      * requires type resolving during cfg construction
    * instructions do not store exception path information, it is only stored
    in catch instructions (each catch stores a list of throwing instructions)
      * does not require type resolving, we can't provide more thorough information
      about exception paths
  * instructions store their mapping to the bytecode
* additional basic block API 
  * basic blocks are just 'views' that are mapped to the range of instructions
  in the original cfg
  * exception paths are represented in basic block API similarly to instructions

## lower priority features

* graph visualization
  * export to dot format at least
* 'dsl' for runtime CFG construction
* API for IR modification, aka 'visitor'
* built-in IR transformations (like replacing `invokedynamic` for `String.concat`)

### potential ideas

* split `calls` into two separate instructions
* come up with the way to build CFG without needing to resolve all types

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
sealed class JcInst {
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
sealed class JcExpr {
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
sealed class JcConstant : JcValue()
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
