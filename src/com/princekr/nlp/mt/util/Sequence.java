package com.princekr.nlp.mt.util;

/**
 * Immutable sequence.
 * <p>
 * Contract: Implementations should provide cheap construction of
 * subsequences.
 * <p>
 * Notes: In the futurem Sequence may be made into a subtype of
 * java.util.Collection or java.util.list. However, right now this would bring
 * with it a lot of methods that aren't really useful given how sequences are used.
 */
public interface Sequence<T> {
}
