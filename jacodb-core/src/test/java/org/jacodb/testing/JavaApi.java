/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.testing;

import org.jacodb.api.JcDatabase;
import org.jacodb.api.cfg.JcArgument;
import org.jacodb.api.cfg.JcExpr;
import org.jacodb.api.cfg.TypedExprResolver;
import org.jacodb.impl.JacoDB;
import org.jacodb.impl.JcCacheSettings;
import org.jacodb.impl.JcSettings;
import org.jacodb.impl.features.Usages;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class JavaApi {
    private static class ArgumentResolver extends TypedExprResolver<JcArgument> {

        @Override
        public void ifMatches(@NotNull JcExpr jcExpr) {
            if (jcExpr instanceof JcArgument) {
                getResult().add((JcArgument) jcExpr);
            }
        }

    }

    public static void cacheSettings() {
        new JcCacheSettings().types(10, Duration.of(1, ChronoUnit.MINUTES));
    }

    public static void getDatabase() {
        try {
            JcDatabase instance = JacoDB.async(new JcSettings().installFeatures(Usages.INSTANCE)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
