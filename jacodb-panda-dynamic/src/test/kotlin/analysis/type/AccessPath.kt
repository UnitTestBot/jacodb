package analysis.type

import org.jacodb.analysis.ifds.Accessor
import org.jacodb.analysis.ifds.ElementAccessor
import org.jacodb.analysis.ifds.FieldAccessor
import org.jacodb.panda.dynamic.ets.base.EtsArrayAccess
import org.jacodb.panda.dynamic.ets.base.EtsCastExpr
import org.jacodb.panda.dynamic.ets.base.EtsConstant
import org.jacodb.panda.dynamic.ets.base.EtsEntity
import org.jacodb.panda.dynamic.ets.base.EtsInstanceFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsLocal
import org.jacodb.panda.dynamic.ets.base.EtsParameterRef
import org.jacodb.panda.dynamic.ets.base.EtsStaticFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsThis
import org.jacodb.panda.dynamic.ets.base.EtsValue

data class AccessPath(val base: AccessPathBase, val accesses: List<Accessor>) {
    operator fun plus(accessor: Accessor) = AccessPath(base, accesses + accessor)
    operator fun plus(accessors: List<Accessor>) = AccessPath(base, accesses + accessors)

}

sealed interface AccessPathBase {
    object This : AccessPathBase
    object Static : AccessPathBase
    data class Arg(val index: Int) : AccessPathBase
    data class Local(val name: String) : AccessPathBase
    data class Const(val constant: EtsConstant) : AccessPathBase
}

fun EtsEntity.toBase(): AccessPathBase = when (this) {
    is EtsConstant -> AccessPathBase.Const(this)
    is EtsLocal -> AccessPathBase.Local(name)
    is EtsThis -> AccessPathBase.This
    is EtsParameterRef -> AccessPathBase.Arg(index)
    else -> error("$this is not access path base")
}

fun EtsEntity.toPathOrNull(): AccessPath? = when (this) {
    is EtsConstant -> AccessPath(AccessPathBase.Const(this), emptyList())

    is EtsLocal -> AccessPath(AccessPathBase.Local(name), emptyList())

    is EtsThis -> AccessPath(AccessPathBase.This, emptyList())

    is EtsParameterRef -> AccessPath(AccessPathBase.Arg(index), emptyList())

    is EtsArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is EtsInstanceFieldRef -> {
        instance.toPathOrNull()?.let {
            it + FieldAccessor(field.name)
        }
    }

    is EtsStaticFieldRef -> {
        AccessPath(AccessPathBase.Static, listOf(FieldAccessor(field.name, isStatic = true)))
    }

    is EtsCastExpr -> arg.toPathOrNull()

    else -> null
}

fun EtsEntity.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
