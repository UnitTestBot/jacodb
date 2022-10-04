package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypedField

class JcTypedFieldImpl(
    override val ownerType: JcRefType,
    override val field: JcField,
    override val fieldType: JcType,
    override val name: String
) : JcTypedField