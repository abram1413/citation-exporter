package gov.ncbi.pmc.ids;

import gov.ncbi.pmc.cite.BadParamException;

/**
 * This stores information about a particular ID as requested by the user.
 *
 * Implementation note:  I thought about having this extend IdGlob, rather than
 * contain an instance, but it won't work.  The IdGlobs are cached, and once
 * created, need to be independent of any given request.
 */
public class RequestId {
    // The original value, as entered by the user
    private String originalValue;

    /**
     * The type and the canonicalized value (after changes in case, adding
     * default prefix, etc.) are both stored in the Identifier object.
     */
    private Identifier canonical;

    /// When this ID gets resolved, this points to this IdGlob
    private IdGlob idGlob;

    /// Constructor, default id type is "aiid".
    public RequestId(String value)
        throws BadParamException
    {
        this("aiid", value);
    }

    /// Constructor
    public RequestId(String type, String value)
        throws BadParamException
    {
        this(new Identifier(type, value));
    }

    /// Constructor
    public RequestId(Identifier canonical) {
        this(canonical.getValue(), canonical);
    }

    /**
     *  Most general constructor, when the original value used to create the
     * Identifier doesn't necessarily match the canonical value.
     */
    public RequestId(String originalValue, Identifier canonical) {
        this.originalValue = originalValue;
        this.canonical = canonical;
        idGlob = new IdGlob();
        idGlob.addId(canonical);
    }

    public String getType() {
        return canonical.getType();
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public Identifier getCanonical() {
        return canonical;
    }


    public boolean hasType(String type) {
        return idGlob.hasType(type);
    }

    /**
     * Get an Identifier, given the type.  Note that this takes versions
     * into account.  If this has a parent (meaning that this is a version-specific
     * ID, and it doesn't have the requested type, but it's parent does, then the
     * parent's value is returned.
     */
    public Identifier getIdByType(String type) {
        return idGlob.getIdByType(type);
    }

    /**
     * This function is similar, but allows you to provide a list of types. If
     * there is no Identifier of the first type, then it tries the second type,
     * until one is found; or returns null.
     */
    public Identifier getIdByTypes(String[] types) {
        Identifier id;
        for (String t : types) {
            if ((id = getIdByType(t)) != null) return id;
        }
        return null;
    }

    public void setGood(boolean good) {
        idGlob.setGood(good);
    }

    public boolean isGood() {
        return idGlob.isGood();
    }

    public boolean isVersioned() {
        return idGlob.isVersioned();
    }

    /**
     * Used by the IdResolver during resolution.
     */
    public IdGlob getIdGlob() {
        return idGlob;
    }

    /**
     * Set the IdGlob.  This will throw an IllegalStateException if the IdGlob has already
     * been set, or and IllegalArgumentException if it doesn't
     * have an identifier that exactly matches canonical.
     */
    public void setIdGlob(IdGlob idGlob)
        throws IllegalStateException, IllegalArgumentException
    {
        Identifier id = idGlob.getIdByType(getType());
        if (id == null || !id.equals(canonical))
            throw new IllegalArgumentException("Error in RequestId; id of type " + getType() +
                    " doesn't match expected");
        this.idGlob = idGlob;
    }

    public String toString() {
        String r = "{ originalValue: " + originalValue + ", canonical: " + canonical +
                   ", idGlob: " + idGlob + " }";
        return r;
    }
}
