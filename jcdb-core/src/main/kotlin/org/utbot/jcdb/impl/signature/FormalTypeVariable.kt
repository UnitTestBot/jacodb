package org.utbot.jcdb.impl.signature

interface FormalTypeVariable

class Formal(val symbol: String? = null, val boundTypeTokens: List<GenericType>? = null) : FormalTypeVariable