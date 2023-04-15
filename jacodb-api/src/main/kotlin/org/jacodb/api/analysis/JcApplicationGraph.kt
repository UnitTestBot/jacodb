package org.jacodb.api.analysis

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst

interface JcApplicationGraph : ApplicationGraph<JcMethod, JcInst>, JcAnalysisPlatform