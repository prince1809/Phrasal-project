package com.princekr.nlp.mt.decoder;

/**
 * Interface for decoding algorithms.
 */
public interface Inferer<TK, FV> {

    public static enum NbestMode {
        Standard, Diverse, Combined
    }
}
