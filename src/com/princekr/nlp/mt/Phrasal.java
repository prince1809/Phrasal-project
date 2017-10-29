package com.princekr.nlp.mt;

import com.princekr.nlp.mt.decoder.Inferer;
import com.princekr.nlp.mt.decoder.Inferer.NbestMode;
import com.princekr.nlp.mt.decoder.feat.FeatureExtractor;
import com.princekr.nlp.mt.decoder.recomb.RecombinationFilterFactory;
import com.princekr.nlp.mt.decoder.util.Scorer;
import com.princekr.nlp.mt.lm.LanguageModel;
import com.princekr.nlp.mt.process.Postprocessor;
import com.princekr.nlp.mt.process.Preprocessor;
import com.princekr.nlp.mt.tm.TranslationModel;
import com.princekr.nlp.mt.util.IOTools;
import com.princekr.nlp.mt.util.IString;
import com.princekr.nlp.mt.util.InputProperties;
import com.princekr.nlp.mt.util.RichTranslation;
import com.princekr.nlp.mt.util.Sequence;
import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        sb.append("Usage: java ").append(Phrasal.class.getName()).append(" OPTS [ini_file] < input > output").append(nl).append(nl)
                .append("Phrasal: A phrase-based machine translation decoder from the stanford NLP group.").append(nl).append(nl)
                .append("Command-line arguments override arguments specified in the optional ini_file:").append(nl).append(nl)
                .append("  -").append(INPUT_FILE_OPT).append(" file : Filename of the file to decode").append(nl)
                .append("  -").append(TRANSLATION_TABLE_OPT).append(" filename : Translation model file. Multiple file can be specified by separating filenames with colons").append(nl)
                .append("  -").append(LANGUAGE_MODEL_OPT).append(" filename : Language model file. For KenLM, prefix filename with 'kenlm:'").append(nl)
                .append("  -").append(OPTION_LIMIT_OPT).append(" num : Translation option limit.").append(nl)
                .append("  -").append(NBEST_LIST_OPT).append(" num : n-best list size").append(nl)
                .append("  -").append(DISTINCT_NBEST_LIST_OPT).append(" boolean : Generate distinct n-best lists (default: false)").append(nl)
                .append("  -").append(FORCE_DECODE).append(" filename [filename] : Force decode to reference files(s).").append(nl)
                .append("  -").append(PREFIX_ALIGN_COMPOUNDS).append(" boolean : Apply heuristic compound word alignment for prefix decoding? Affects cube pruning decoder only. (default: false)").append(nl)
                .append("  -").append(BEAM_SIZE).append(" num : Stack/beam size.").append(nl)
                .append("  -").append(SEARCH_ALGORITHM).append(" [cube|multibeam] : Inference algorithm (default: cube)").append(nl)
                .append("  -").append(REORDERING_MODEL).append(" type filename [options] : Lexicalized re-ordering model where type is [class|hierarchical]. Multiple models can be separating filenames with colons.");
        return sb.toString();
    }

    private static final Logger logger = LogManager.getLogger(Phrasal.class);

    public static final String INPUT_FILE_OPT = "text";
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
     * multithreading inside main decoding loop. Generally, it is better
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

    /**
     * Inference objects, one per thread
     */
    private List<Inferer<IString, String>> inferers;

    /**
     * Holds the model weights one per inferer. The model weights have a shared
     * feature index.
     */
    private List<Scorer<String>> scorers;

    /**
     * The feature extractor.
     */
    private FeatureExtractor<IString, String> featurizer;

    /**
     * Phrase table / translation model
     */
    private TranslationModel<IString, String> translationModel;

    /**
     * Language model
     */
    private LanguageModel<IString> languageModel;

    /**
     * Whether to filter unknown words in the output
     */
    private boolean dropUnknownWords = false;

    /**
     * @return true if unknown words are dropped, and false otherwise.
     */
    public boolean isDropUnknownWords() {
        return dropUnknownWords;
    }


    /**
     * n-best list options
     */
    private String nbestListOutputType = "moses";
    private Pattern nBestListFeaturePattern = null;
    private PrintStream nbestListWriter;
    private int nbestListSize;
    private boolean distinctNbest = false;
    private NbestMode nbestMode = NbestMode.Standard;

    /**
     * Internal alignment options
     */
    private PrintStream alignmentWriter;

    /**
     * References for force decoding
     */
    private List<List<Sequence<IString>>> forceDecodeReferences;

    /**
     * Hard limit on inputs to be decoded
     */
    private int maxSentenceSize = Integer.MAX_VALUE;
    private int minSentenceSize = 0;

    /**
     * Output model score to console.
     */
    private boolean printModelScores = false;

    /**
     * Properties of each input when Phrasal is run on a finite input file.
     */
    private List<InputProperties> inputPropertiesList;

    /**
     * Recombination configuration
     */
    private String recombinationMode = RecombinationFilterFactory.EXACT_RECOMBINATION;

    /**
     * Add boundary token flag.
     */
    private boolean wrapBoundary;

    /**
     * For simulating KSR and word prediction accuracy.
     */
    private int ksr_nbest_size;
    private int wpa_nbest_size;
    private int oracle_nbest_size;
    private String references;

    /**
     * Pre/post processing filters.
     */
    private Preprocessor preprocessor;
    private Postprocessor postprocessor;

    public Preprocessor getPreprocessor() {
        return preprocessor;
    }

    public Postprocessor getPostprocessor() {
        return postprocessor;
    }

    /**
     * Set the global model used by Phrasal.
     *
     * @param m
     */
    public void setModel(Counter<String> m) {
        this.globalModel = m;
    }

    /**
     * Return the global Phrasal model.
     *
     * @return
     */
    public Counter<String> getModel() {
        return this.globalModel;
    }

    /**
     * @return the number of threads specified in the ini file.
     */
    public int getNumThreads() {
        return numThreads;
    }

    /**
     * Access the decoder's phrase table
     *
     * @return
     */
    public TranslationModel<IString, String> getTranslationModel() {
        return translationModel;
    }

    /**
     * Access the decoder's language model
     *
     * @return
     */
    public LanguageModel<IString> getLanguageModel() {
        return languageModel;
    }

    /**
     * Return the input properties loaded with ini file.
     *
     * @return
     */
    public List<InputProperties> getInputProperties() {
        return Collections.unmodifiableList(inputPropertiesList);
    }

    /**
     * Return the nbest list size specified in the ini file.
     *
     * @return
     */
    public int getNbestListSize() {
        return nbestListSize;
    }

    /**
     * @return The wrap boundary property specified in the ini file.
     */
    public boolean getWrapBoundary() {
        return wrapBoundary;
    }

    public static void initStaticMembers(Map<String, List<String>> config) {

    }

    public Phrasal(Map<String, List<String>> config)
            throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, SecurityException,
            InvocationTargetException, NoSuchMethodException, ClassNotFoundException {
        this(config, null);
    }

    public Phrasal(Map<String, List<String>> config, LanguageModel<IString> lm) {
        // Check for required parameters
        if (!config.keySet().containsAll(REQUIRED_FIELDS)) {
            final Set<String> missingFields = new HashSet<>(REQUIRED_FIELDS);
            missingFields.retainAll(config.keySet());
            logger.fatal("The following required fields are missing: {}", missingFields);
            throw new RuntimeException();
        }
    }

    /**
     * Lightweight container for decoder input.
     */
    public static class DecoderInput {
        //public final Sequence<IString> source;
    }

    /**
     * Lightweight container for decoder output.
     */
    public static class DecoderOutput {

    }

    /**
     * Wrapper class to submit this decoder instance to the thread pool.
     */
    private class PhrasalProcessor implements ThreadsafeProcessor<DecoderInput, DecoderOutput> {

        @Override
        public DecoderOutput process(DecoderInput input) {
            return null;
        }

        @Override
        public ThreadsafeProcessor<DecoderInput, DecoderOutput> newInstance() {
            return null;
        }
    }

    /**
     * Output the result of decodeFromConsole(), and write to the n-best list if
     * necessary.
     * NOTE: This call is *not* threadsafe.
     *
     * @param translations    n-best list
     * @param bestTranslation if post-processing has been applied, then this is post-processed
     *                        sequence at the top of the n-best list.
     * @param sourceLength
     * @param sourceInputId
     */
    private void processConsoleResult(List<RichTranslation<IString, String>> translations,
                                      Sequence<IString> bestTranslation, int sourceLength, int sourceInputId) {

    }

    /**
     * Decode input from inputStream and either write 1-best transactions to
     * stdout or return them in a <code>List</code>.
     *
     * @param inputStream
     * @param outputToConsole if true, output the 1-best transactions to the console. Otherwise,
     *                        return them in a <code>List</code>
     * @return
     */
    public List<RichTranslation<IString, String>> decode(InputStream inputStream, boolean outputToConsole) {
        return null;
    }

    /**
     * Decode a tokenzied input string. Returns an n-best list of transactions as
     * specified by the decoders <code>nbestListSize</code> parameter.
     *
     * @param source
     * @param sourceInputId
     * @param threadId
     * @return
     */
    public List<RichTranslation<IString, String>> decode(Sequence<IString> source, int sourceInputId, int threadId) {
        return null;
    }


    /**
     * Read a combination of config file and other command line arguments,
     * Command line arguments supercede those specified in the config file.
     *
     * @param configFile
     * @param options
     * @return
     */
    private static Map<String, List<String>> getConfigurationFrom(String configFile, Properties options) {
        final Map<String, List<String>> config = configFile == null ? new HashMap<>() : IOTools.readConfigFile(configFile);
        // Command-line options supersede config file options
        options.entrySet().stream().forEach(e -> config.put(e.getKey().toString(),
                Arrays.asList(e.getValue().toString().split("\\s+"))));
        return config;
    }


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
