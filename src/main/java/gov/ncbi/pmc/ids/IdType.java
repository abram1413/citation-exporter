package gov.ncbi.pmc.ids;

import static gov.ncbi.pmc.ids.IdType.CType.NOOP;
import static gov.ncbi.pmc.ids.IdType.CType.REPLACEMENT;
import static gov.ncbi.pmc.ids.IdType.CType.UPPERCASE;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An IdType object has a name (e.g. "pmcid") and provides methods for
 * checking whether or not value strings match IDs of this type, and
 * for creating Identifier objects from those strings.
 *
 * The name is used as the prefix in a "curie" style ID string (e.g.
 * "pmcid:PMC123456"). A valid IdType name must begin with a lowercase
 * letter, and must contain only lowercase letters and numerals. In
 * all public methods that deal with ID value strings, they can
 * be given with a prefix or non-prefixed.
 *
 * An IdType object includes a list of "Checkers", each of which has
 * a regular expression pattern that is used to
 * identify valid ID value strings, data describing
 * how to canonicalize those value strings, and a boolean flag indicating
 * whether or not IDs matching that pattern are versioned.
 */

public class IdType {
    private static Logger log = LoggerFactory.getLogger(IdType.class);

    /**
     * Reference to the IdDb that this IdType belongs to
     */
    private final IdDb _iddb;

    /**
     * The name; only lowercase letters and numbers; must begin with a letter.
     */
    private final String _name;

    /**
     * This enumeration describes, for a given Checker, how to
     * canonicalize the value string.
     */
    public static enum CType {
        NOOP,         // value is already canonical
        UPPERCASE,    // convert to uppercase
        REPLACEMENT,  // apply a replacement string with back-references
    }

    public static class Checker {
        private final Pattern _pattern;
        private final CType _cType;
        private final String _replacement;
        private final boolean _isVersioned;

        /**
         * Constructor.
         */
        public Checker(String patternStr, CType cType, String replacement,
                boolean isVersioned)
        {
            _pattern = Pattern.compile(patternStr);
            _cType = cType;
            if ((cType == NOOP || cType == UPPERCASE) &&
                    replacement != null)
                throw new IllegalArgumentException(
                        "Invalid arguments to Checker constructor");
            if (cType == REPLACEMENT && replacement == null)
                throw new IllegalArgumentException("Missing replacement pattern");
            _replacement = replacement;
            _isVersioned = isVersioned;
        }

        /**
         * Get the regular expression pattern used to match ID value strings.
         */
        public Pattern getPattern() {
            return _pattern;
        }

        /**
         * Get the CType enumeration that describes how value strings are
         * canonicalized.
         */
        public CType getCType() {
            return _cType;
        }

        /**
         * Get the replacement string.
         */
        public String getReplacement() {
            return _replacement;
        }

        /**
         * Get the flag indicating whether or not ID value string matching
         * this pattern are versioned.
         */
        public boolean isVersioned() {
            return _isVersioned;
        }

        @Override
        public int hashCode() {
            return Objects.hash(_pattern.pattern(), _cType, _replacement,
                    _isVersioned);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof Checker)) return false;
            Checker chk = (Checker) obj;
            return
                _pattern.pattern().equals(chk._pattern.pattern()) &&
                _cType == chk._cType &&
                _replacement.equals(chk._replacement) &&
                _isVersioned == chk._isVersioned;
        }

        @Override
        public String toString() {
            return "'" + _pattern.toString() + "' -> " +
                ( _cType == CType.NOOP ? "<noop>" :
                  _cType == CType.UPPERCASE ? "<uppercase>" :
                  _cType == CType.REPLACEMENT ? ("'" + _replacement + "'") : ""
                ) +
                ", " + (_isVersioned ? "" : "not ") + "versioned";
        }
    }

    /**
     * The list of checkers.
     */
    private final List<Checker> _checkers;

    /**
     * IdTypes shouldn't be instantiated directly; they should be
     * created at the same time the IdDb object is created.
     */
    public IdType(IdDb iddb, String name, List<Checker> checkers)
    {
        _iddb = iddb;

        // Validate: type names must be all
        // lower case, must start with a letter, and only contain letters, numbers,
        // and underscores
        if (!name.toLowerCase().equals(name)) throw new IllegalArgumentException(
                "Invalid IdType name");
        char c0 = name.charAt(0);
        if (c0 < 'a' || c0 > 'z') throw new IllegalArgumentException(
                "Invalid IdType name");
        char[] chars = name.toCharArray();
        for (char c : chars) {
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_')
                throw new IllegalArgumentException("Invalid IdType name");
        }
        _name = name;

        _checkers = checkers;
    }

    /**
     * Get the IdDb to which this type belongs.
     */
    public IdDb getIdDb() {
        return _iddb;
    }

    /**
     * Get the name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Get the List of Checkers
     */
    public List<Checker> getCheckers() {
        return _checkers;
    }

    /**
     * When a given Checker successfully matches a non-prefixed value
     * string, we'll create one of these objects to store the data,
     * in case we need it later.
     */
    protected class CheckerMatcher {
        IdType idType;
        Checker checker;
        Matcher matcher;
        String npVal;
        CheckerMatcher(IdType t, Checker c, Matcher m, String npv) {
            idType = t;
            checker = c;
            matcher = m;
            npVal = npv;
        }

        /**
         * Create a new Identifier from a successful check/match
         */
        Identifier newId() {
            CType ct = checker._cType;
            String canon =
                ct == CType.NOOP ? npVal :
                ct == CType.UPPERCASE ? npVal.toUpperCase() :
                    matcher.replaceFirst(checker._replacement);
            return new Identifier(idType, canon, checker._isVersioned);
        }
    }

    /**
     * This helper function checks a non-prefixed value string (which
     * must not be null) against the list of checker patterns.
     */
    protected CheckerMatcher _checkMatch(String npVal) {
        for (Checker c : _checkers) {
            Matcher m = c._pattern.matcher(npVal);
            if (m.matches()) return new CheckerMatcher(this, c, m, npVal);
        }
        return null;
    }

    /**
     * Returns true if the value string matches any of the patterns
     * for this IdType. If the value is prefixed, then the prefix is
     * checked to make sure it matches this type.
     */
    public boolean matches(String value)
    {
        return _iddb.valid(this, value);
    }

    /**
     * Create a new Identifier of this type from a value string
     * (with or without prefix).
     * If the value doesn't match any of the patterns for this IdType,
     * or if it includes a prefix and that doesn't match, then
     * this returns null.
     */
    public Identifier id(String value)
    {
        if (value == null) return null;
        Identifier _id = _iddb.id(this, value);
        return _id;
    }

    @Override
    public String toString() {
        return _name;
    }

    /**
     * This produces a more descriptive String.
     */
    public String dump() {
        String checkers = _checkers.stream()
                .map(Checker::toString)
                .collect(Collectors.joining(" | "));
        return "IdType<" + _name + ">: [ " + checkers + " ]";
    }

    @Override
    public int hashCode() {
        return _name.hashCode();
    }

    /**
     * Two IdTypes are equal if they have the same name. The IdDb enforces
     * only one IdType with a given name, and it's the app's responsibility
     * to make sure it doesn't mix IdDbs.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof IdType)) return false;
        return _name.equals(((IdType) obj)._name);
    }
}
