package com.princekr.nlp.mt;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Phrasal: a phrase-based machine translation system from the Stanford University NLP Group.
 * <p>
 * Note: This object is not threadsafe. To enable programmatic multithreading
 * with Phrasal, specify the number of threads in the *.ini as usual, then use
 * the threadId arguments in the decode() functions to submit to the underlying
 * threadpool. This design permits storage of the LM and phrase table--among
 * other large data structures--in the shared memory.
 */
public class Phrasal {

    private static String usage() {
        final StringBuilder sb = new StringBuilder();
        final String nl = System.getProperty("line.separator");
        sb.append("Usage: java ");
        return sb.toString();
    }

    private static final Logger logger = LogManager.getLogger(Phrasal.class);

    public static final String INPUT_FILE_OPT ="text";
    public static final String TRANSLATION_TABLE_OPT = "ttable-file";
    public static final String LANGUAGE_MODEL_OPT = "lmodel-file";
    public static final String OPTION_LIMIT_OPT = "ttable-limit";
    public static final String NBEST_LIST_OPT = "n-best-list";
    public static final String DISTINCT_NBEST_LIST_OPT = "distint-n-best-list";
    public static final String FORCE_DECODE = "force-decode";
    public static final String PREFIX_ALIGN_COMPOUNDS = "prefix-align-compounds";
    public static final String BEAM_SIZE = "stack";
    public static final String SEARCH_ALGORITHM = "search-algorithm";
    public static final String REORDERING_MODEL = "reordering-model";
    public static final String WEIGHTS_FILE = "weights-file";
    public static final String MAX_SENTENCE_LENGTH = "max-sentence-length";
    public static final String MIN_SENTENCE_LENGTH = "min-sentence-length";
    public static final String DISTORTION_LIMIT = "distortion-limit";
    public static final String ADDITIONAL_FEATURIZERS = "additional-featurizers";
    public static final String NUM_THREADS = "threads";
    public static final String USE_ITG_CONSTRAINTS = "use-itg-constraints";
    public static final String RECOMBINATION_MODE = "recombination-mode";
    public static final String GAPS_OPT = "gaps";
    public static final String MAX_PENDING_PHRASES_OPT = "max-pending-phrases";
    public static final String GAPS_IN_FUTURE_COST_OPT = "gaps-in-future-cost";
    public static final String LINEAR_DISTORTION_OPT = "linear-distortion-options";
    public static final String DROP_UNKNOWN_WORDS = "drop-unknown-words";
    public static final String INDEPENDENT_PHRASE_TABLES = "independent-phrase-tables";


    private static final Set<String> REQUIRED_FIELDS = new HashSet<>();
    private static final Set<String> OPTIONAL_FIELDS = new HashSet<>();
    private static final Set<String> ALL_RECOGNIZED_FIELDS = new HashSet<>();

    static {
        REQUIRED_FIELDS.add(TRANSLATION_TABLE_OPT);
        OPTIONAL_FIELDS.addAll(Arrays.asList(INPUT_FILE_OPT, WEIGHTS_FILE));
        ALL_RECOGNIZED_FIELDS.addAll(REQUIRED_FIELDS);
        ALL_RECOGNIZED_FIELDS.addAll(OPTIONAL_FIELDS);
    }

    public static final String TM_BACKGROUND_NAME = "background-tm";
    public static final String TM_FOREGROUND_NAME = "foreground-tm";
    public static final String TM_TERMBASE_NAME = "termbase-tm";

    public static final int MAX_NBEST_SIZE = 1000;


    /**
     * Number of decoding threads. Setting this parameter to 0 enables
     * multithreading insdie main decoding loop. Generally, it is better
     * to set the desired number of threads here (i.e., set this parameter >= 1)
     */
    private int numThreads = 1;

    /**
     * Hard distortion limit for phrase-based decoder
     */
    private int distortionLimit = 5;

    /**
     * Maximum phrase table query size per span.
     */
    private int ruleQueryLimit = 20;

    /**
     * Global model landed at startup.
     */
    private Counter<String> globalModel;

    private static List<String> gapOpts = null;
    public static boolean withGaps = false;

    //private final List<IString>



    /**
     * Run phrasal from the command line.
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        final Properties options = StringUtils.argsToProperties(args);
        final String configFile = options.containsKey("") ? (String) options.get("") : null;
        options.remove("");
        if ((options.size() == 0 && configFile == null) || options.containsKey("help") || options.containsKey("h")) {
            System.err.println(usage());
            System.exit(-1);
        }

        // by default exit on uncaught exception
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            logger.fatal("Uncaught top-level exception", ex);
            System.exit(-1);
        });

       // final Map<String, List<String>> configuration = getConfigurationFrom(configFile, options);
    }
}
