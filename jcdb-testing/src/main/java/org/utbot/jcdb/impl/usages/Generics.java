package org.utbot.jcdb.impl.usages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Generics<T extends List<Object>> {

    private T niceField;
    private List<? extends T> niceList;

    public <W extends Collection<T>> void merge(Generics<T> generics) {
    }

    public <W extends Collection<T>> W merge1(Generics<T> generics) {
        return null;
    }

    public void xxx(Generics<T> generics, T lol) {
        lol.add(new ArrayList<>());
    }

}