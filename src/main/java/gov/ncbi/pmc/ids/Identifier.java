package gov.ncbi.pmc.ids;

import java.util.Objects;

/**
 * This stores a canonicalized, immutable identifier value. It has a
 * reference to its IdType, and a flag indicating whether or not it is
 * versioned.
 */
public class Identifier {
    private final IdType _type;
    private final String _canon;   // canonicalized, but without prefix
    private final boolean _isVersionSpecific;

    /**
     * Identifiers can't be constructed directly. Use one of the IdType
     * or the IdDb makeId() methods.
     *
     * This constructor is used by those classes to create a new object
     * with an already-canonicalized value string.
     */
    protected Identifier(IdType type, String canon, boolean isVersionSpecific)
    {
        _type = type;
        _canon = canon;
        _isVersionSpecific = isVersionSpecific;
    }

    public IdDb getIdDb() {
        return _type.getIdDb();
    }

    public IdType getType() {
        return _type;
    }

    public String getValue() {
        return _canon;
    }

    public boolean isVersionSpecific() {
        return _isVersionSpecific;
    }

    public String getCurie() {
        return getType().getName() + ":" + getValue();
    }

    @Override
    public String toString() {
        return getCurie();
    }

    @Override
    public int hashCode() {
        return Objects.hash(_type, _canon);
    }

    /**
     * To be equal, the identifiers must match type and value.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Identifier)) return false;
        Identifier id = (Identifier) obj;
        return _type.equals(id._type) && _canon.equals(id._canon);
    }

    public boolean sameId(Identifier oid) {
        return this.equals(oid);
    }
    public boolean sameId(IdSet oset) {
        return oset.sameId(this);
    }
}
