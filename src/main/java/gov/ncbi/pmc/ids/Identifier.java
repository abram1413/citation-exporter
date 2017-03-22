package gov.ncbi.pmc.ids;

import org.antlr.v4.runtime.misc.NotNull;

import gov.ncbi.pmc.cite.BadParamException;

/**
 * This stores a canonicalized ID, that can be instantiated in a number of
 * ways.
 */
public class Identifier {
    private final String type;
    private final String value;

    // Here we specify the regexp patterns that will be used to match IDs
    // to their type The order is important:  if determining the type of an
    // unknown id (getIdType()), then these regexps are attempted in order,
    // and first match wins.
    private final static String[][] idTypePatterns = {
        { "pmid", "^\\d+$" },
        { "pmcid", "^([Pp][Mm][Cc])?\\d+(\\.\\d+)?$" },
        { "mid", "^[A-Za-z]+\\d+$" },
        { "doi", "^10\\.\\d+\\/.*$" },
        { "aiid", "^\\d+$" },
    };

    /**
     * Check a purported ID type string to make sure it is one we know about.
     */
    public static boolean idTypeValid(String type) {
        for (int idtn = 0; idtn < idTypePatterns.length; ++idtn) {
            String[] idTypePattern = idTypePatterns[idtn];
            if (type.equals(idTypePattern[0])) return true;
        }
        return false;
    }

    /**
     * This method checks the id value string, attempting
     * to match it against the regular expressions listed above, to determine
     * its type. It throws an exception if it can't find a match.
     */
    public static String matchIdType(String idStr)
        throws BadParamException
    {
        for (int idtn = 0; idtn < idTypePatterns.length; ++idtn) {
            String[] idTypePattern = idTypePatterns[idtn];
            if (idStr.matches(idTypePattern[1])) {
                return idTypePattern[0];
            }
        }
        throw new BadParamException("Invalid id.");
    }

    /**
     * Checks to see if the id string matches the given type's pattern
     */
    public static boolean idTypeMatches(String idStr, String idType) {
        for (int idtn = 0; idtn < idTypePatterns.length; ++idtn) {
            String[] idTypePattern = idTypePatterns[idtn];
            if (idTypePattern[0].equals(idType) &&
                idStr.matches(idTypePattern[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a new Identifier object from a value string, which might or
     * might not be a curie. If not, then this attempts to determine the type
     * by matching it to the patterns above. If it matches more than one, and
     * one of them is the default type "aiid", then it uses that.
     */
    public Identifier(@NotNull String _value)
        throws BadParamException
    {
        this(_value, "pmid");
    }

    /**
     * Create a new Identifier object.  This validates and canonicalizes
     * the value given.
     *
     * FIXME: Why not a constructor that takes a CURIE (e.g. "pmid:123456")?
     */
    public Identifier(@NotNull String _value, @NotNull String defaultType)
        throws BadParamException
    {
        System.out.println("======================> _value: " + _value);
        if (!idTypeValid(defaultType)) {
            throw new BadParamException("Id type not recognized.");
        }
      /*
        if (!idTypeMatches(_value, defaultType)) {
            throw new BadParamException("This doesn't look like a valid " +
                "id for this type.");
        }
      */
        String cvalue = null;
        if (defaultType.equals("pmcid")) {
            if (_value.matches("\\d+")) {
                cvalue = "PMC" + _value;
            }
            else {
                cvalue = _value.toUpperCase();
            }
        }
        else if (defaultType.equals("mid")) {
            cvalue = _value.toUpperCase();
        }
        else {
            cvalue = _value;
        }

        this.type = defaultType;
        this.value = cvalue;
    }

    public String getType() {
        return type;
    }
    public String getValue() {
        return value;
    }

    public String getCurie() {
        return type + ":" + value;
    }

    @Override
    public String toString() {
        return getCurie();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = prime * ((type == null) ? 0 : type.hashCode()) +
                ((value == null) ? 0 : value.hashCode());
        return result;
    }

    /**
     * Strict equals - the identifers must exactly match type and value.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Identifier id = (Identifier) obj;
        return type.equals(id.getType()) && value.equals(id.getValue());
    }
}
