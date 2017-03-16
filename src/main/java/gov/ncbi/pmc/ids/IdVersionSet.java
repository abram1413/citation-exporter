package gov.ncbi.pmc.ids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Objects of this class store a group of version-specific Identifiers.
 */
public class IdVersionSet extends IdSet {

    /**
     * The non-version-specific parent
     */
    private final IdNonVersionSet _parent;

    /**
     * True if this is the current version
     */
    private final boolean _isCurrent;

    ////////////////////////////////////////
    // Constructors and builder methods

    /**
     * Create a new version-specific IdGlob, and add this to the list
     * of the parent's kids.
     */
    public IdVersionSet(IdDb iddb, IdNonVersionSet parent, boolean isCurrent) {
        super(iddb);
        _parent = parent;
        _isCurrent = isCurrent;
        _parent._addVersion(this);
    }

    @Override
    public IdVersionSet add(Identifier... ids) {
        return (IdVersionSet) this._add(ids);
    }

    ////////////////////////////////////////
    // Getters

    /**
     * Returns true because this IdGlob is version-specific.
     */
    @Override
    public boolean isVersionSpecific() {
        return true;
    }

    /**
     * Get the non-version-specific parent IdGlob
     */
    public IdNonVersionSet getParent() {
        return _parent;
    }

    /**
     * Returns true if this is the current version
     */
    public boolean isCurrent() {
        return _isCurrent;
    }

    /**
     * Get the complement
     */
    @Override
    public IdSet getComplement() {
        return _isCurrent ? _parent : null;
    }

    /**
     * Get the IdGlob in this cluster that corresponds to the current
     * version-specific child (will either be this or a sibling)
     */
    @Override
    public IdVersionSet getCurrentVersion() {
        return _isCurrent ? this : _parent.getCurrentVersion();
    }

    @Override
    public IdNonVersionSet getNonVersioned() {
        return _parent;
    }


    ////////////////////////////////////////
    // Info about IDs

    @Override
    public Stream<IdSet> _workSetStream() {
        Stream<IdVersionSet> siblings = _parent.getKidsStream()
            .filter(sib -> sib != this);
        return Stream.concat(
            Stream.of(this, _parent),
            siblings
        );
    }

    /////////////////////////////////////////////////////////////////
    // Equality and comparison methods

    /**
     * Render this IdGlob as a string.
     */
    @Override
    public String toString() {
        return "{ " + _idsToString() + " }";
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
