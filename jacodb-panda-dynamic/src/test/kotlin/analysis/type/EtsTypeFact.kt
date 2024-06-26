package analysis.type

import org.jacodb.panda.dynamic.ets.base.EtsType

sealed interface EtsTypeFact {
    fun union(other: EtsTypeFact): EtsTypeFact {
        if (this == other) return this

        return when {
            this is ObjectEtsTypeFact && other is ObjectEtsTypeFact -> union(this, other)
            this is UnionEtsTypeFact -> union(this, other)
            this is IntersectionEtsTypeFact -> union(this, other)
            other is UnionEtsTypeFact -> union(other, this)
            other is IntersectionEtsTypeFact -> union(other, this)
            else -> mkUnionType(this, other)
        }
    }

    fun intersect(other: EtsTypeFact): EtsTypeFact? {
        if (this == other) return this

        if (other is UnknownEtsTypeFact) return this
        if (other is AnyEtsTypeFact) return other

        return when (this) {
            is UnknownEtsTypeFact -> other
            is AnyEtsTypeFact -> this
            is StringEtsTypeFact -> when (other) {
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                else -> null
            }

            is NumberEtsTypeFact -> when (other) {
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                else -> null
            }

            is FunctionEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> mkIntersectionType(this, other)
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                else -> null
            }

            is ObjectEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> intersect(this, other)
                is FunctionEtsTypeFact -> mkIntersectionType(this, other)
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                else -> null
            }

            is UnionEtsTypeFact -> intersect(this, other)
            is IntersectionEtsTypeFact -> intersect(this, other)
        }
    }

    fun replaceUnknownWithAny(): EtsTypeFact

    object UnknownEtsTypeFact : EtsTypeFact {
        override fun toString(): String = "unknown"
        override fun replaceUnknownWithAny(): EtsTypeFact = AnyEtsTypeFact
    }

    object AnyEtsTypeFact : EtsTypeFact {
        override fun toString(): String = "any"
        override fun replaceUnknownWithAny(): EtsTypeFact = this
    }

    object StringEtsTypeFact : EtsTypeFact {
        override fun toString(): String = "string"
        override fun replaceUnknownWithAny(): EtsTypeFact = this
    }

    object NumberEtsTypeFact : EtsTypeFact {
        override fun toString(): String = "number"
        override fun replaceUnknownWithAny(): EtsTypeFact = this
    }

    object FunctionEtsTypeFact : EtsTypeFact {
        override fun toString(): String = "function"
        override fun replaceUnknownWithAny(): EtsTypeFact = this
    }

    data class ObjectEtsTypeFact(val cls: EtsType?, val properties: Map<String, EtsTypeFact>) : EtsTypeFact {
        override fun replaceUnknownWithAny(): EtsTypeFact =
            ObjectEtsTypeFact(cls, properties.mapValues { (_, type) -> type.replaceUnknownWithAny() })
    }

    data class UnionEtsTypeFact(val types: Set<EtsTypeFact>) : EtsTypeFact {
        override fun replaceUnknownWithAny(): EtsTypeFact =
            mkUnionType(types.mapTo(hashSetOf()) { it.replaceUnknownWithAny() })
    }

    data class IntersectionEtsTypeFact(val types: Set<EtsTypeFact>) : EtsTypeFact {
        override fun replaceUnknownWithAny(): EtsTypeFact =
            mkIntersectionType((types.mapTo(hashSetOf()) { it.replaceUnknownWithAny() }))
    }

    companion object {
        private fun intersect(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact? {
            // todo: push intersection
            return mkIntersectionType(unionType, other)
        }

        private fun intersect(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact? {
            val result = hashSetOf<EtsTypeFact>()
            for (type in intersectionType.types) {
                val intersection = type.intersect(other) ?: return null
                if (intersection is IntersectionEtsTypeFact) {
                    result.addAll(intersection.types)
                } else {
                    result.add(intersection)
                }
            }
            return mkIntersectionType(result)
        }

        private fun intersect(obj1: ObjectEtsTypeFact, obj2: ObjectEtsTypeFact): EtsTypeFact? {
            val intersectionProperties = obj1.properties.toMutableMap()
            for ((property, type) in obj2.properties) {
                val currentType = intersectionProperties[property]
                if (currentType == null) {
                    intersectionProperties[property] = type
                    continue
                }

                intersectionProperties[property] = currentType.intersect(type) ?: return null
            }

            val intersectionCls = obj1.cls.takeIf { it == obj2.cls }
            return ObjectEtsTypeFact(intersectionCls, intersectionProperties)
        }

        private fun union(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
            val result = hashSetOf<EtsTypeFact>()
            for (type in unionType.types) {
                val union = type.union(other)
                if (union is UnionEtsTypeFact) {
                    result.addAll(union.types)
                } else {
                    result.add(union)
                }
            }
            return mkUnionType(result)
        }

        private fun union(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
            // todo: push union
            return mkUnionType(intersectionType, other)
        }

        private fun union(obj1: ObjectEtsTypeFact, obj2: ObjectEtsTypeFact): EtsTypeFact {
            if (obj1.cls != null && obj2.cls != null && obj1.cls != obj2.cls) {
                return mkUnionType(obj1, obj2)
            }

            val commonProperties = obj1.properties.keys.intersect(obj2.properties.keys).associateWith { property ->
                val thisType = obj1.properties.getValue(property)
                val otherType = obj2.properties.getValue(property)
                thisType.union(otherType)
            }

            val o1OnlyProperties = obj1.properties.filter { it.key !in obj2.properties }
            val o2OnlyProperties = obj2.properties.filter { it.key !in obj1.properties }

            val commonCls = obj1.cls.takeIf { it == obj2.cls }
            val commonObject = ObjectEtsTypeFact(commonCls, commonProperties)

            val o1 = ObjectEtsTypeFact(obj1.cls, o1OnlyProperties)
            val o2 = ObjectEtsTypeFact(obj2.cls, o2OnlyProperties)

            if (commonProperties.isEmpty()) {
                return mkUnionType(o1, o2)
            }

            if (o1OnlyProperties.isEmpty() && o2OnlyProperties.isEmpty()) {
                return commonObject
            }

            return mkIntersectionType(commonObject, mkUnionType(o1, o2))
        }

        private fun mkUnionType(vararg types: EtsTypeFact): EtsTypeFact = mkUnionType(types.toHashSet())

        private fun mkUnionType(types: Set<EtsTypeFact>): EtsTypeFact {
            if (types.size == 1) return types.single()
            return UnionEtsTypeFact(types)
        }

        private fun mkIntersectionType(vararg types: EtsTypeFact): EtsTypeFact = mkIntersectionType(types.toHashSet())

        private fun mkIntersectionType(types: Set<EtsTypeFact>): EtsTypeFact {
            if (types.size == 1) return types.single()
            return IntersectionEtsTypeFact(types)
        }
    }
}
