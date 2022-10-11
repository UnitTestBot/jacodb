package org.utbot.jcdb.impl.types;

import java.io.Closeable;
import java.io.InputStream;
import java.util.List;

public class ClassWithInners<T extends Closeable> {

    public static class Static extends ClassWithInners<InputStream> {

    }

    public class Inner {
        public Integer state = 1;
        public T stateT = null;
        public List<T> stateListT = null;
    }

    public Inner inner;

}


