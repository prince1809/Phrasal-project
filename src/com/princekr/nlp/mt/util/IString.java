package com.princekr.nlp.mt.util;

import java.io.Serializable;

/**
 * Represents a String with a corresponding Integer ID. Keeps a static index of
 * all the Strings, indexed by ID.
 */
public class IString implements CharSequence, Serializable, Comparable<IString> {

    //private final int id;


    @Override
    public int length() {
        return 0;
    }

    @Override
    public char charAt(int index) {
        return 0;
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return null;
    }

    @Override
    public int compareTo(IString o) {
        return 0;
    }
}
