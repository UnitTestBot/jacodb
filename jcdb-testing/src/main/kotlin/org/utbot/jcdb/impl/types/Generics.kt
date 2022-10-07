package org.utbot.jcdb.impl.types

open class Generics1<T, W : List<T>> {

    var state: T? = null
    var stateW: W? = null
    var stateListW: List<W>? = null
}


class PartialParametrization<W : List<String>> : Generics1<String, W>()