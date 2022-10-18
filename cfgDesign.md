# Design draft for JCDB control flow graph implementation

```kotlin
/**
 * class that represents CFG of a method
 */
class JcGraph {
    /**
     * internally I think this will be represented just as an ArrayList
     * there is no need to store it as basic array
     */
    val instructions: List<JcInstruction>

    /**
     * simple way to access all entries/exits of a graph
     */
    val instructionEntry: JcInstruction
    val instructionExits: List<JcInstruction>
    val exceptionExits: List<JcExceptionReceiver>

    /**
     * basic blocks of a graph, computed on request
     */
    val basicBlocks: List<JcBasicBlock>
    // todo
    // maybe this should be converted into a function
    // that explicitly states that basic blocks are computed on request
    
    // todo
    //need to decide how to keep basic blocks synced with
    // the JcGraph in case it has been changed
    
    // todo
    // also we need to provide entry/exit access for
    // basic block API, but we need to decide about handling exceptions
    // in basic blocks first
}

class JcBasicBlock { // todo: maybe JcBlock is better?
    // internal implementation details
    /**
     * still considering storing a reference to a JcGraph in blocks
     * I think that if we store it inside CFG elements, the API will be much better
     */
    internal var graph: JcGraph? = null
    
    /**
     * internally basic block just stores the range of indices
     */
    internal var startIndexInclusive: Int
    internal var endIndexExclusive: Int
    
    // API
    /**
     * instructions that belong to this block
     */
    val instructions: List<JcInstruction>

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
    
    // todo
    // also, should basic blocks provide a way to modify
    // the CFG, or should just be 'views' that do not have
    // modification interface? In my opinion, latter is better
}

/**
 * represents an exception receiver in a method. It can be
 * either a JcCatchInstruction (if there is a catch instruction in a method
 * that handles thrown exception) or a synthetic exit node in a graph
 */
interface JcExceptionReceiver

/**
 * represents an instruction of control flow graph
 * example implementations:
 * - JcAssignInstruction
 * - JcGotoInstruction
 * - JcIfInstruction
 * - JcCallInstruction
 * - JcReturnInstruction
 * - etc.
 */
sealed class JcInstruction { // todo: maybe JcInst is better?
    // internal implementation details
    /**
     * still considering storing a reference to a JcGraph in instructions
     * it does complicate the serialization/deserialization a little and has
     * other small disadvantages. But I think that if we store it inside
     * CFG elements, the API will be much better
     */
    internal var graph: JcGraph? = null

    /**
     * index of an instruction in the JcGraph
     */
    internal var index: Int = -1

    // API
    /**
     * simple way to get *all* the JcExpressions of an instruction
     * sometimes it is convenient, need to think do we need it or not
     */
    val operands: List<JcExpression>

    // 'raw' instruction list api
    /**
     * previous instruction in the raw instruction list
     */
    val previous: JcInstruction

    /**
     * next instruction in the raw instruction list
     */
    val next: JcInstruction

    // control flow api
    /**
     * incoming edges in the control flow of a method
     */
    val predecessors: List<JcInstruction>

    /**
     * outgoing edges in the control flow of a method
     */
    val successors: List<JcInstruction>

    /**
     * implicit outgoing edges, that indicate control
     * flow during exception occurrence
     */
    val implicitSuccessors: List<JcExceptionReceiver>
    
    // todo
    // need to decide do we want instructions to be mutable or not
}

/**
 * represents an expression used in the JcInstruction
 * example implementations:
 * - JcAddExpression, JcSubExpression, etc.
 * - JcValue
 * - etc.
 */
sealed class JcExpression // todo: maybe JcExpr is better?

/**
 * represents a single value
 * example implementations:
 * - JcConstant
 * - JcLocalVariable
 * - JcArgument
 * - JcThis
 */
sealed class JcValue : JcExpression()
```
