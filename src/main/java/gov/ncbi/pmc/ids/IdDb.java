package gov.ncbi.pmc.ids;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import gov.ncbi.pmc.ids.IdType.CType;
import gov.ncbi.pmc.ids.IdType.Checker;
import gov.ncbi.pmc.ids.IdType.CheckerMatcher;

/**
 * This is the main entry point into the Identifier library. You can instantiate an
 * IdDb programmatically, or from a JSON file. An IdDb comprises a set of IdTypes,
 * and provides methods for matching String values to those ID types, canonicalizing
 * them, etc.
 */
public class IdDb {
    private static Logger log = LoggerFactory.getLogger(IdDb.class);

    // A name for this collection of ID types
    private String _name;

    // All IdTypes, in precedence order
    private ArrayList<IdType> _types = new ArrayList<IdType>();

    private Map<String, IdType> _byName = new HashMap<String, IdType>();

    /**
     * One default IdDb is pre-initialized, and contains IdTypes used by
     * literature resources.
     */
    public static final IdDb litIds = new IdDb("literature-ids");

    // Instantiation is a two-step process, because it involves circular references.
    // (The IdType children each have a reference back to this IdDb parent.)
    static {
        // The order in which the IdTypes appear
        // determines which regular expressions get tried first. For example,
        // the string "PMC12345" matches the pattern for both pmcid and mid,
        // but it will be tried against pmcid first.
        litIds.addTypes(
            new ArrayList<IdType>(Arrays.asList(
                new IdType(litIds, "pmid", new ArrayList<Checker>(Arrays.asList(
                    new Checker("^\\d+$", CType.NOOP, null, false),
                    new Checker("^\\d+(\\.\\d+)?$", CType.NOOP, null, true)
                ))),
                new IdType(litIds, "pmcid", new ArrayList<Checker>(Arrays.asList(
                    new Checker("^(\\d+)$", CType.REPLACEMENT, "PMC$1", false),
                    new Checker("^(\\d+(\\.\\d+)?)$", CType.REPLACEMENT, "PMC$1", true),
                    new Checker("^([Pp][Mm][Cc]\\d+)$", CType.UPPERCASE, null, false),
                    new Checker("^([Pp][Mm][Cc]\\d+(\\.\\d+)?)$", CType.UPPERCASE, null, true)
                ))),
                new IdType(litIds, "mid", new ArrayList<Checker>(Arrays.asList(
                    new Checker("^([A-Za-z]+\\d+)$", CType.UPPERCASE, null, true)
                ))),
                new IdType(litIds, "doi", new ArrayList<Checker>(Arrays.asList(
                    new Checker("^(10\\.\\d+\\/.*)$", CType.NOOP, null, false)
                ))),
                new IdType(litIds, "aiid", new ArrayList<Checker>(Arrays.asList(
                    new Checker("^(\\d+)$", CType.NOOP, null, true)
                )))
            ))
        );
    }

    /**
     * Construct with a name. Because the data structure has circular
     * references, it needs to be created without IdTypes first. The
     * IdTypes are then instantiated and added later.
     */
    public IdDb(String name) {
        _name = name;
    }

    /**
     * Add an IdType to this database.
     */
    public void addType(IdType type) {
        _types.add(type);
        _byName.put(type.getName(), type);
    }

    /**
     * Add a list of IdTypes to this database.
     */
    public void addTypes(List<IdType> types) {
        for (IdType type : types) addType(type);
    }

    /**
     * Factory method to read a new IdDb from a JSON string.
     */
    public static IdDb fromJson(String jsonString)
        throws IOException
    {
        return (new IdDbJsonReader()).readIdDb(jsonString);
    }

    /**
     * Factory method to read a new IdDb from a JSON resource
     */
    public static IdDb fromJson(URL jsonUrl)
        throws JsonProcessingException, IOException
    {
        return (new IdDbJsonReader()).readIdDb(jsonUrl);
    }

    /**
     * Get the name of this ID database
     */
    public String getName() {
        return _name;
    }

    /**
     * How many ID types?
     */
    public int size() {
        return _types.size();
    }

    /**
     * Get an IdType by its name (case-insensitive).
     * @return - null if the argument is null; otherwise it returns the
     *   IdType whose name matches.
     * @throws IllegalArgumentException - if the argument is non-null
     *   but there's no match.
     */
    public IdType getType(String name)
    {
        if (name == null) return null;
        IdType t = lookupType(name);
        if (t == null) throw new IllegalArgumentException("Bad ID type name");
        return t;
    }

    /**
     * Look up an IdType by its name (case-insensitive).
     * @return - null if the argument is null or if there's no match.
     */
    public IdType lookupType(String name) {
        return (name == null) ? null : _byName.get(name.toLowerCase());
    }

    /**
     * Get the complete list of types.
     */
    public List<IdType> getAllTypes() {
        return _types;
    }

    /**
     * Possible problems when processing arguments that specify Identifiers.
     */
    public enum IdSpecProblem {
        BAD_TYPE_ARG,
        BAD_TYPE_PREFIX,
        TYPE_MISMATCH,
    };

    /**
     * Helper class used to process arguments for specifying identifiers.
     */
    public class IdSpec {
        private IdType type = null;
        private String npVal;
        private List<IdSpecProblem> problems = new ArrayList<IdSpecProblem>();

        /**
         * Process a single ID value string argument.
         * @param value  An ID value string, with or without a prefix. Not null.
         */
        public IdSpec(String value) {
            this((IdType) null, value);
        }

        /**
         * Helper function that does most of the work
         */
        private void _process(IdType argType, String value) {
            // Split the value string into prefix and non-prefixed parts
            String[] parts = value.split(":", 2);
            boolean hasPrefix = (parts.length == 2);
            npVal = hasPrefix ? parts[1] : value;

            // Convert the prefix (if given) into an IdType
            IdType prefixType = hasPrefix ? lookupType(parts[0]) : null;
            if (hasPrefix && prefixType == null)
                problems.add(IdSpecProblem.BAD_TYPE_PREFIX);

            // Reconcile the types
            if (argType != null && prefixType != null &&
                !argType.equals(prefixType))
                problems.add(IdSpecProblem.TYPE_MISMATCH);

            // Only set the idType if there haven't been any data problems
            if (problems.size() == 0)
                type = (argType == null) ? prefixType : argType;
        }

        /**
         * Process a String argument for the type and a value string.
         * @param typeName  The name of a type. null means no IdType was
         *   explicitly specified.
         * @param value  An ID value string, with or without a prefix. Not null.
         */
        public IdSpec(String typeName, String value) {
            IdType type = lookupType(typeName);
            if (typeName != null && type == null)
                problems.add(IdSpecProblem.BAD_TYPE_ARG);
            this._process(type, value);
        }

        /**
         * Process an IdType argument for the type and a value string.
         * @param type  Specifies the type. null means no type was
         *   specified explicitly.
         * @param value  An ID value string, with or without a prefix. Not null.
         */
        public IdSpec(IdType type, String value) {
            this._process(type, value);
        }

        /// Get the specified IdType
        public IdType getType() { return type; }

        /// Get the non-prefixed value
        public String getNpVal() { return npVal; }

        /// True if there are problems with the type specifiers
        public boolean hasProblems() { return problems.size() > 0; }
    }

    /**
     * Helper function that returns a stream of CheckerMatcher objects
     * corresponding to an ID specifier.
     */
    private Stream<CheckerMatcher> _cmStream(IdSpec idSpec) {
        // First create a stream of candidate IdType objects
        IdType specType = idSpec.getType();
        Stream<IdType> typeStream =
            idSpec.hasProblems() ? Stream.of() :
            specType == null ? _types.stream() :
                Stream.of(specType);

        return typeStream
            .map(t -> t._checkMatch(idSpec.getNpVal()))
            .filter(Objects::nonNull);
    }

    /**
     * Find the type of this ID.
     * @return the first IdType matching the ID value string, or
     *   null if it can't find a match.
     */
    public IdType findType(String value)
    {
        return _findType(new IdSpec(value));
    }

    /**
     * Find the first IdType matching an ID specifier.
     * Returns null if it can't find a match.
     */
    public IdType findType(String typeName, String value) {
        return _findType(new IdSpec(typeName, value));
    }

    /**
     * Find the first IdType matching an ID specifier.
     * Returns null if it can't find a match.
     */
    public IdType findType(IdType idType, String value) {
        return _findType(new IdSpec(idType, value));
    }

    /**
     * Implements the findType() methods.
     */
    private IdType _findType(IdSpec spec) {
        return _cmStream(spec)
                .map(cm -> cm.idType)
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds all the IdTypes that this value string matches.
     * @param value  An ID value string, with or without a prefix. Not null.
     */
    public List<IdType> findAllTypes(String value) {
        IdSpec spec = new IdSpec(value);
        return _cmStream(spec)
                .map(cm -> cm.idType)
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the value string is a valid identifier (of any
     * type).
     */
    public boolean valid(String value) {
        return _valid(new IdSpec(value));
    }

    /**
     * Returns true if the value string looks like a valid identifier
     * of the indicated type.
     */
    public boolean valid(String typeName, String value) {
        return _valid(new IdSpec(typeName, value));
    }

    /**
     * Returns true if the value string looks like a valid identifier
     * of the indicated type.
     */
    public boolean valid(IdType idType, String value) {
        return _valid(new IdSpec(idType, value));
    }

    /**
     * Returns true if the value string looks like a valid identifier
     * of the indicated type.
     */
    private boolean _valid(IdSpec spec) {
        return _findType(spec) != null;
    }


    /**
     * Create a new Identifier object from a value string.
     * @param value  An ID value string, with or without a prefix. Not null.
     */
    public Identifier id(String value)
    {
        if (value == null) return null;
        return _id(new IdSpec(value));
    }

    /**
     * Create an Identifier object of the specified type and value string
     * @param typeName  Specifies an IdType by name (case-insensitive)
     * @param value  An ID value string, with or without a prefix. Not null.
     */
    public Identifier id(String typeName, String value)
    {
        if (value == null) return null;
        return _id(new IdSpec(typeName, value));
    }

    /**
     * Create an Identifier object of the specified type and value string.
     * @param idType  Specifies an IdType
     * @param value  An ID value string, with or without a prefix. Not null.
     */
    public Identifier id(IdType idType, String value)
    {
        if (value == null) return null;
        return _id(new IdSpec(idType, value));
    }

    /**
     * Private helper function that implements the id() methods above.
     */
    private Identifier _id(IdSpec spec) {
        return _cmStream(spec)
                .map(CheckerMatcher::newId)
                .findFirst()
                .orElse(null);
    }
}

