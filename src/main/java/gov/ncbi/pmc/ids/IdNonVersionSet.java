package gov.ncbi.pmc.ids;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * Objects of this class store a group of Identifiers that all refer to
 * the same non-version-specific resource.
 */
public class IdNonVersionSet extends IdSet {

    /**
     * References to the version-specific children.
     */
    private List<IdVersionSet> _versionedKids;

    /**
     * If this is non-version-specific, this will refer to the current
     * version
     */
    private IdVersionSet _currentVersion;

    ////////////////////////////////////////
    // Constructors and builder methods

    /**
     * Create a new non-version-specific IdGlob
     */
    public IdNonVersionSet(IdDb iddb) {
        super(iddb);
        _versionedKids = new ArrayList<IdVersionSet>();
        _currentVersion = null;
    }

    @Override
    public IdNonVersionSet add(Identifier... ids) {
        return (IdNonVersionSet) this._add(ids);
    }

    /**
     * Add a versioned IdGlob as a child of this IdGlob. No checks
     * are made to see if the argument is already in the list or not.
     * This should only be called from the IdGlobVer constructor.
     */
    protected void _addVersion(IdVersionSet vkid) {
        _versionedKids.add(vkid);
        if (vkid.isCurrent()) _currentVersion = vkid;
    }

    ////////////////////////////////////////
    // Getters

    /**
     * Returns false.
     */
    @Override
    public boolean isVersionSpecific() {
        return false;
    }

    @Override
    public IdSet getComplement() {
        return _currentVersion;
    }

    @Override
    public IdVersionSet getCurrent() {
        return _currentVersion;
    }

    @Override
    public IdNonVersionSet getNonVersioned() {
        return this;
    }


    public List<IdVersionSet> getVersions() {
        return _versionedKids;
    }

    /////////////////////////////////////////////////////////////////
    // Info about IDs

    // Helper function: stream all the kids in reverse order.
    //--------------------- tested -----------------------------
    private Stream<IdVersionSet> _allKidsReversed() {
        // Start with an integer stream in reverse order
        int top = _versionedKids.size();
        IntStream rev = IntStream.range(0, top).map(i -> top - i - 1);
        return rev.mapToObj(i -> _versionedKids.get(i));
    }

    /**
     * Get all of the version-specific children as a Stream.
     * The current version, if not null, is put first, then the others
     * in reverse order.
     */
    //--------------------- tested -----------------------------
    public Stream<IdVersionSet> getKidsStream() {
        if (_currentVersion == null) {
            return this._allKidsReversed();
        }
        else {
            return Stream.concat(
                Stream.of(_currentVersion),
                this._allKidsReversed()
                    .filter(k -> k != _currentVersion)
            );
        }
    }

    /**
     * This implements the getGlobStream(WORK) method for
     * non-version-specific IdGlobs.
     */
    //--------------------- tested -----------------------------
    @Override
    public Stream<IdSet> _workSetStream() {
        return Stream.concat(
            Stream.of(this),
            this.getKidsStream()
        );
    }


    /**
     * Render this IdGlob as a string.
     */
    @Override
    public String toString() {
        String kidsString = _versionedKids.stream()
                .map(IdVersionSet::toString)
                .collect(Collectors.joining(", "));
        return "{ " + _idsToString() + ", versions: [ " +
            kidsString + " ] }";
    }

}
