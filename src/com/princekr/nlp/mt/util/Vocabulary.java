package com.princekr.nlp.mt.util;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;

/**
 * Created by prince on 2017/10/28.
 */
public class Vocabulary implements Serializable, KryoSerializable {

    public static final Logger logger = LogManager.getLogger(Vocabulary.class.getName());

    private static final int INITIAL_SYSTEM_CAPACITY = 1000000;
    private static Index<String> systemIndex = new ConcurrentHashIndex<String>(INITIAL_SYSTEM_CAPACITY);
    private static final int UNKNOWN_ID = ConcurrentHashIndex.UNKNOWN_ID;

    private static final int INITIAL_CAPACITY = 10000;
    protected Index<String> index;


    @Override
    public void write(Kryo kryo, Output output) {

    }

    @Override
    public void read(Kryo kryo, Input input) {

    }
}
