package gov.ncbi.pmc.ids;

import gov.ncbi.pmc.cite.BadParamException;

/**
 * This stores a canonicalized ID.
 */
public class Identifier {
    private final String type;
    private final String value;

    // Here we specify the regexp patterns that will be used to match IDs
    // to their type The order is important:  if determining the type of an
    // unknown id (getIdType()), then these regexps are attempted in order,
    // and first match wins.
    protected static String[][] idTypePatterns = {
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
     * This method checks the id string to see what type it is, by attempting
     * to match it against the regular expressions listed above.  It throws
     * an exception if it can't find a match.
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
        throw new BadParamException("Invalid id: " + idStr);
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
     * Create a new Identifier object.  This validates and canonicalizes
     * the value given.
     */
    public Identifier(String type, String value)
        throws BadParamException
    {
        if (!idTypeValid(type)) {
            throw new BadParamException("Id type '" + type +
                "' not recognized");
        }
        if (!idTypeMatches(value, type)) {
            throw new BadParamException("Id: '" + value +
                "' doesn't look like a valid id of type '" + type + "'");
        }
        String cvalue = null;
        if (type.equals("pmcid")) {
            if (value.matches("\\d+")) {
                cvalue = "PMC" + value;
            }
            else {
                cvalue = value.toUpperCase();
            }
        }
        else if (type.equals("mid")) {
            cvalue = value.toUpperCase();
        }
        else {
            cvalue = value;
        }

        this.type = type;
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

    public boolean equals(Identifier id) {
        return type.equals(id.getType()) && value.equals(id.getValue());
    }

    @Override
    public String toString() {
        return getCurie();
    }
}
