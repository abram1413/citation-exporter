package gov.ncbi.pmc.ids;

import java.util.Objects;

/**
 * This stores a canonicalized, immutable identifier value. It has a
 * reference to its IdType, and a flag indicating whether or not it is
 * versioned.
 */
public class Identifier {
    private final IdType type;
    private final String value;   // canonicalized, but without prefix
    private final boolean versionSpecific;

    /**
     * Identifiers can't be constructed directly. Use one of the IdType
     * or the IdDb makeId() methods.
     *
     * This constructor is used by those classes to create a new object
     * with an already-canonicalized value string.
     */
    protected Identifier(IdType type, String value, boolean isVersionSpecific)
    {
        this.type = type;
        this.value = value;
        this.versionSpecific = isVersionSpecific;
    }

    public IdDb getIdDb() {
        return this.type.getIdDb();
    }

    public IdType getType() {
        return this.type;
    }

    public String getValue() {
        return this.value;
    }

    public boolean isVersionSpecific() {
        return this.versionSpecific;
    }

    public String getCurie() {
        return getType().getName() + ":" + getValue();
    }

    @Override
    public String toString() {
        return getCurie();
    }

    /**
     * To be equal, the identifiers must match type and value, but not
     * _isVersionSpecific. (It would be an error to have two Identifiers with
     * the same type and value but different _isVersionSpecific.)
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Identifier)) return false;
        Identifier id = (Identifier) obj;
        return type.equals(id.type) && value.equals(id.value);
    }
}
