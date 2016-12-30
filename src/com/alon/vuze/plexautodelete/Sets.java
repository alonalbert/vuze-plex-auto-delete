package com.alon.vuze.plexautodelete;

import java.util.Collections;
import java.util.HashSet;
import java.util.SortedSet;
import java.util.TreeSet;

@SuppressWarnings("WeakerAccess")
public class Sets {

    @SuppressWarnings("unused")
    public static <K> HashSet<K> newHashSet() {
        return new HashSet<>();
    }

    @SafeVarargs
    @SuppressWarnings("unused")
    public static <E> HashSet<E> newHashSet(E... elements) {
        int capacity = elements.length * 4 / 3 + 1;
        HashSet<E> set = new HashSet<>(capacity);
        Collections.addAll(set, elements);
        return set;
    }

    @SuppressWarnings("unused")
    public static <E> SortedSet<E> newSortedSet() {
        return new TreeSet<>();
    }

    @SafeVarargs
    @SuppressWarnings("unused")
    public static <E> SortedSet<E> newSortedSet(E... elements) {
        SortedSet<E> set = new TreeSet<>();
        Collections.addAll(set, elements);
        return set;
    }

}
