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

package org.jacodb.testing.hierarchies;

public interface Creature {

    void say(String smth);

    default void hello() {
        say("Hello");
    }

    interface Animal extends Creature {
    }

    interface Fish extends Creature {
        @Override
        void say(String smth);
    }

    interface Dinosaur extends Creature, Animal {
    }

    interface Bird extends Creature {
    }

    class DinosaurImpl implements Dinosaur {
        @Override
        public void say(String smth) {
            System.out.println("Dino say:" + smth);
        }
    }

    class TRex extends DinosaurImpl implements Dinosaur {
        @Override
        public void say(String smth) {
            super.say("TRex say:" + smth);
        }

        @Override
        public void hello() {
            // do nothing
        }
    }

    class Pterodactyl extends DinosaurImpl implements Bird {
        @Override
        public void say(String smth) {
            super.say("Pterodactyl say:" + smth);
        }
    }


}
