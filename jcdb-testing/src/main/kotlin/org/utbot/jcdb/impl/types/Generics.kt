package org.utbot.jcdb.impl.types

open class LinkedGenerics<T, W : List<T>> {

    var state: T? = null
    var stateW: W? = null
    var stateListW: List<W>? = null
}


class PartialParametrization<W : List<String>> : LinkedGenerics<String, W>()