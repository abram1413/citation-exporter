package gov.ncbi.pmc.ids;

import static gov.ncbi.pmc.ids.IdSet.IdScope.IDENTITY;
import static gov.ncbi.pmc.ids.IdSet.IdScope.INSTANCE;
import static gov.ncbi.pmc.ids.IdSet.IdScope.WORK;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Instances of this class store a list of identifiers that all refer to
 * the same semantic resource. For example, a journal article
 * (non-version-specific) can have a pmid, a pmcid, and/or a doi, and
 * these all refer to the exact same semantic resource. A specific
 * version of the journal article can have a pmid (with version number),
 * a pmcid (again, with version number), a mid, and/or an aiid.
 *
 * So, there are two types of IdSets:
 *
 * 1. non-version-specific - correspond roughly to a FRBR "work". For
 *   example, the ID "PMC3159421" does not refer to any particular version.
 *   Non-versioned IdSets might or might not have a list of versioned
 *   IdSet "children".
 * 2. version-specific - corresponds roughly to a FRBR "expression", for
 *   example, the ID "PMC3159421.1" is the first version of the
 *   above-mentioned article. Every versioned IdSet will have a reference
 *   to its parent (non-version-specific) IdSet.
 *
 * Note also that, at any given time that a non-version-specific ID is
 * queried, it will resolve to a specific version (usually the latest).
 * This is captured with the _currentVersion property.
 */
public abstract class IdSet {
    private final IdDb iddb;

    /**
     * Cross reference from an ID type to an Identifier object
     */
    protected Map<IdType, Identifier> _idByType;


    ////////////////////////////////////////
    // Constructors and builder methods

    /**
     * Create a new IdSet
     */
    public IdSet(IdDb iddb) {
        this.iddb = iddb;
        this._idByType = new HashMap<>();
    }

    /**
     * This method implements add()
     */
    protected IdSet _add(Identifier...ids) {
        for (Identifier id : ids) {
            if (id == null) continue;

            // If they're trying to add the same ID twice, do nothing
            if (this.sameId(id)) continue;

            // If it's a different ID of the same type, that's bad
            IdType type = id.getType();
            if (_idByType.get(type) != null)
                throw new IllegalArgumentException(
                    "IdSet already contains an ID of type " + type.getName());

            // The IDs must match in version-specificity
            if (this.isVersionSpecific() != id.isVersionSpecific())
                throw new IllegalArgumentException(
                    "Attempt to add a new non-version-specific Identifier " +
                    "to a version-specific IdSet.");

            // All good
            _idByType.put(type, id);
        }
        return this;
    }

    /**
     * Add new Identifier(s) to this IdSet.
     * @param id  A list of Identifiers.
     * @throws IllegalArgumentException if this IdSet already has a different
     *   Identifier with the same type, or if the version-specificity
     *   doesn't match.
     */
    public abstract IdSet add(Identifier... ids);


    ////////////////////////////////////////
    // Getters

    /**
     * Returns true if this IdSet is version-specific.
     */
    public abstract boolean isVersionSpecific();

    /**
     * Get the "complement" IdSet, which is the other IdSet in
     * this cluster that refers to the same version of the resource,
     * if it exists. The complement is determined as follows:
     * - If `this` is a non-version-specific parent, the complement is
     *   `_currentVersion`.
     * - Otherwise `this` is a version-specific child, and:
     *     - If it is the current version, then the complement is the
     *       parent.
     */
    public abstract IdSet getComplement();

    /**
     * Get the version-specific IdVersionSet associated with the current
     * version of this resource, if is exists. Otherwise, null.
     */
    public abstract IdVersionSet getCurrentVersion();

    /**
     * Get the non-versioned IdNonVersionSet object for this cluster.
     */
    public abstract IdNonVersionSet getNonVersioned();

    /**
     * True if this IdSet has an Identifier of the given type.
     */
    public boolean hasType(IdType type) {
        return _idByType.get(type) != null;
    }


    /////////////////////////////////////////////////////////////////
    // Get IdSets as Streams

    /**
     * Enumeration that specifies the scope of a request for IdSets.
     */
    public enum IdScope {
        IDENTITY,   // just this one
        INSTANCE,   // all IdSets that refer to the same version
        WORK        // all IdSets that refer to the same work
    }

    /**
     * Get a Stream of IdSets, depending on the scope.
     */
    public Stream<IdSet> getSetStream(IdScope scope) {
        if (scope == IDENTITY) return Stream.of(this);
        if (scope == INSTANCE) {
            IdSet c = this.getComplement();
            return c == null ? Stream.of(this) : Stream.of(this, c);
        }
        if (scope == WORK) {
            return this._workSetStream();
        }
        return Stream.of();
    }

    /**
     * Get the IdSets that correspond to this version of
     * the resource, as a Stream. `this` will always be first. If this
     * IdSet has a "complement", then that will be streamed next.
     */
    public Stream<IdSet> getInstanceSetStream() {
        return this.getSetStream(INSTANCE);
    }

    /**
     * Get the IdSets associated with all versions of the resource,
     * as a Stream. They are streamed in the following order:
     * - `this`,
     * - The complement, if there is one,
     * - The version-specific children that haven't been streamed yet.
     *   In this order:
     *     - current
     *     - others from latest to earliest.
     */
    public Stream<IdSet> getWorkSetStream() {
        return this.getSetStream(WORK);
    }

    // Helper function that implements getSetStream(WORK)
    protected abstract Stream<IdSet> _workSetStream();


    /////////////////////////////////////////////////////////////////
    // Get Identifiers as Streams

    /**
     * Helper class to normalize arguments to the methods that fetch
     * Identifiers. Many methods have these three signatures:
     * - () - no arguments: retrieve all matching Identifiers
     * - (type) - retrieve Identifier(s) of the given type
     * - (pred) - filter with a custom Predicate.
     */
    public  class IdFilter {
        public final boolean allTypes;
        public final IdType type;
        public final Predicate<Identifier> pred;

        public IdFilter(boolean allTypes, IdType type,
                Predicate<Identifier> pred)
        {
            this.allTypes = allTypes;
            this.type = type;
            this.pred = pred;
        }
        public IdFilter() {
            this(true, null, null);
        }
        public IdFilter(IdType type) {
            this(false, type, null);
        }
        public IdFilter(Predicate<Identifier> pred) {
            this(true, null, pred);
        }

        @SuppressWarnings("resource")
        public Stream<Identifier> getIdStream(IdSet g) {
            // The source of the stream is either all types, one type,
            // or nothing.
            Stream<Identifier> src;
            if (allTypes) {
                List<IdType> allTypes = iddb.getAllTypes();
                Stream<IdType> typeStream = allTypes.stream();
                Stream<Identifier> idStream = typeStream.map(type -> g._idByType.get(type));
                src = idStream.filter(Objects::nonNull);
            }
            else if (type == null) src = Stream.of();
            else {
                Identifier id = g._idByType.get(type);
                src = id == null ? Stream.of() : Stream.of(id);
            }

            // Filter it on the predicate, if one was given
            Stream<Identifier> filtered =
                pred == null ? src : src.filter(pred);

            // Make sure we don't emit any nulls.
            return filtered.filter(Objects::nonNull);
        }
    }

    /**
     * Get the Identifiers of this IdSet, filtered according to `f`,
     * as a Stream.
     */
    public Stream<Identifier> getIdStream(IdFilter f) {
        return f.getIdStream(this);
    }

    /**
     * Get all Identifiers of this IdSet as a Stream.
     */
    public Stream<Identifier> getIdStream() {
        return getIdStream(new IdFilter());
    }

    /**
     * Get the Identifier of this IdSet, that has the given
     * type, as a Stream. If type == null or if this IdSet doesn't
     * have an Identifier of this type, this produces an empty Stream.
     */
    public Stream<Identifier> getIdStream(IdType type) {
        return getIdStream(new IdFilter(type));
    }

    /**
     * Get the Identifier(s) matching the given Predicate as a Stream.
     */
    public Stream<Identifier> getIdStream(Predicate<Identifier> pred) {
        return getIdStream(new IdFilter(pred));
    }

    // Helper to turn a stream of IdSets into a Stream of Identifiers
    private static Stream<Identifier> _setStreamToIdStream(
        IdFilter f, Stream<IdSet> setStream)
    {
        return setStream
            .map(set -> set.getIdStream(f))
            .flatMap(s -> s);
    }

    /**
     * Get all the Identifiers of this cluster that refer
     * to the same version of the resource, as a Stream.
     */
    public Stream<Identifier> getInstanceIdStream(IdFilter f) {
        return _setStreamToIdStream(f, getInstanceSetStream());
    }

    /**
     * Get all Identifiers associated with this version of the
     * resource as a Stream.
     */
    public Stream<Identifier> getInstanceIdStream() {
        return getInstanceIdStream(new IdFilter());
    }

    /**
     * Get the Identifier(s) of the given type, that are associated with
     * this version of the resource, as a Stream.
     */
    public Stream<Identifier> getInstanceIdStream(IdType type) {
        return getInstanceIdStream(new IdFilter(type));
    }

    /**
     * Get the Identifier(s) matching the given Predicate, that are
     * associated with this version of the resource, as a Stream.
     */
    public Stream<Identifier> getInstanceIdStream(
            Predicate<Identifier> pred)
    {
        return getInstanceIdStream(new IdFilter(pred));
    }

    /**
     * Get the Identifiers associated with all of the versions of
     * this resource, as a Stream.
     */
    public Stream<Identifier> getWorkIdStream(IdFilter f) {
        return _setStreamToIdStream(f, getWorkSetStream());
    }

    public Stream<Identifier> getWorkIdStream() {
        return this.getWorkIdStream(new IdFilter());
    }

    public Stream<Identifier> getWorkIdStream(IdType type) {
        return this.getWorkIdStream(new IdFilter(type));
    }

    public Stream<Identifier> getWorkIdStream(Predicate<Identifier> pred) {
        return this.getWorkIdStream(new IdFilter(pred));
    }


    /////////////////////////////////////////////////////////////////
    // Methods to get Identifiers as a List

    /**
     * Returns a List of Identifiers per the arguments.
     * This is the most general.
     */
    public List<Identifier> getIds(IdScope scope, IdFilter f) {
        Stream<IdSet> setStream = this.getSetStream(scope);
        Stream<Identifier> idStream =
                _setStreamToIdStream(f, setStream);
        //return _streamToList(idStream);
        return idStream.collect(Collectors.toList());
    }

    /**
     * Get Identifiers for just this IdSet as a List
     */
    public List<Identifier> getIds(IdFilter f) {
        return getIds(IDENTITY, f);
    }

    public List<Identifier> getIds() {
        return getIds(IDENTITY, new IdFilter());
    }

    public List<Identifier> getIds(IdType type) {
        return getIds(IDENTITY, new IdFilter(type));
    }

    public List<Identifier> getIds(Predicate<Identifier> pred) {
        return getIds(IDENTITY, new IdFilter(pred));
    }

    /**
     * Get the Identifiers for this IdSet and others.
     */
    public List<Identifier> getInstanceIds(IdFilter f) {
        return getIds(INSTANCE, f);
    }

    public List<Identifier> getInstanceIds() {
        return getIds(INSTANCE, new IdFilter());
    }

    public List<Identifier> getInstanceIds(IdType type) {
        return getIds(INSTANCE, new IdFilter(type));
    }

    public List<Identifier> getInstanceIds(Predicate<Identifier> pred) {
        return getIds(INSTANCE, new IdFilter(pred));
    }

    /**
     * Get the Identifiers for this work.
     */

    public List<Identifier> getWorkIds(IdFilter f) {
        return getIds(WORK, f);
    }

    public List<Identifier> getWorkIds() {
        return getIds(WORK, new IdFilter());
    }

    public List<Identifier> getWorkIds(IdType type) {
        return getIds(WORK, new IdFilter(type));
    }

    public List<Identifier> getWorkIds(Predicate<Identifier> pred) {
        return getIds(WORK, new IdFilter(pred));
    }



    /////////////////////////////////////////////////////////////////
    // Methods to get the first Identifier matching a criterion.

    // Helper to get the first from a stream
    protected static Identifier
        _firstFromStream(Stream<Identifier> str)
    {
        return str.findFirst().orElse(null);
    }

    /**
     * Returns the first Identifier, according to the arguments.
     * This is the most general.
     */
    public Identifier getId(IdScope scope, IdFilter f) {
        Stream<IdSet> globStream = this.getSetStream(scope);
        Stream<Identifier> idStream =
                _setStreamToIdStream(f, globStream);
        return idStream.findFirst().orElse(null);
    }

    /**
     * Get the best Identifier for just this IdSet (IDENTITY scope)
     */
    public Identifier getId(IdFilter f) {
        return getId(IDENTITY, f);
    }

    public Identifier getId() {
        return getId(IDENTITY, new IdFilter());
    }

    public Identifier getId(IdType type) {
        //return getId(IDENTITY, new IdFilter(type));
        return _idByType.get(type);
    }

    public Identifier getId(Predicate<Identifier> pred) {
        return getId(IDENTITY, new IdFilter(pred));
    }

    /**
     * Get the best Identifier for this instance.
     */
    public Identifier getInstanceId(IdFilter f) {
        return getId(INSTANCE, f);
    }

    public Identifier getInstanceId() {
        return getId(INSTANCE, new IdFilter());
    }

    public Identifier getInstanceId(IdType type) {
        return getId(INSTANCE, new IdFilter(type));
    }

    public Identifier getInstanceId(Predicate<Identifier> pred) {
        return getId(INSTANCE, new IdFilter(pred));
    }

    /**
     * This bonus method gets only the Identifiers that are of
     * the given type and are either version specific, or
     * non-version specific.
     */
    public Identifier getInstanceId(IdType type, boolean isVersionSpecific) {
        return getInstanceId(
            id -> id.getType().equals(type) &&
                  id.isVersionSpecific() == isVersionSpecific);
    }

    /**
     * Get the best Identifiers for this work.
     */

    public Identifier getWorkId(IdFilter f) {
        return getId(WORK, f);
    }

    public Identifier getWorkId() {
        return getId(WORK, new IdFilter());
    }

    public Identifier getWorkId(IdType type) {
        return getId(WORK, new IdFilter(type));
    }

    public Identifier getWorkId(Predicate<Identifier> pred) {
        return getId(WORK, new IdFilter(pred));
    }


    /////////////////////////////////////////////////////////////////
    // Equality and comparison methods

    /**
     * Two IdSets are equals if and only if they have exactly the
     * same set of Identifiers. To test whether or not they refer
     * to exactly the same resource, use sameId().
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (this == other) return true;

        if (!(other instanceof IdSet)) return false;
        IdSet oset = (IdSet) other;
        if (this.isVersionSpecific() != oset.isVersionSpecific()) return false;

        if (_idByType.size() != oset._idByType.size()) return false;
        return _idByType.entrySet().stream()
            .allMatch(entry -> {
                IdType type = entry.getKey();
                Identifier myId = entry.getValue();
                Identifier oid = oset._idByType.get(type);
                return oid != null && myId.equals(oid);
            });
    }

    /**
     * Returns true if the Identifier for this IdSet that is of
     * the same type as the argument, is equal to the argument.
     *
     * Note that if two IdSets are equal, they could be merged.
     *
     * @param other  An IdSet or an Identifier.
     */
    public boolean sameId(Identifier id) {
        if (id == null) return false;
        Identifier myId = getId(id.getType());
        return myId != null && myId.equals(id);
    }

    /**
     * This IdSet identifies exactly the same resource as another
     * IdSet iff any of the Identifiers in this IdSet are equal to
     * any of the Identifiers in the other IdSet.
     */
    public boolean sameId(IdSet oset) {
        if (oset == null) return false;
        return this.getIds().stream()
            .anyMatch(myId -> oset.sameId(myId));
    }

    public boolean sameId(Object other) {
        if (other == null) return false;
        if (other instanceof Identifier)
            return this.sameId((Identifier) other);
        if (other instanceof IdSet)
            return this.sameId((IdSet) other);
        return false;
    }

    // Helper for sameInstance
    private boolean _sameInstanceNarrow(Object other) {
        return this.getInstanceSetStream()
                .anyMatch(mySet -> mySet.sameId(other));
    }

    /**
     * Returns true if this IdSet and the other thing both refer
     * to the same version of the same resource.
     */
    public boolean sameInstance(Object other) {
        if (other == null) return false;
        if (other instanceof Identifier)
            return this._sameInstanceNarrow(other);
        if (other instanceof IdSet) {
            IdSet oset = (IdSet) other;
            return oset.getInstanceSetStream()
                .anyMatch(osib -> this._sameInstanceNarrow(osib));
        }
        return false;
    }

    /**
     * Returns true if this IdSet and the other thing both refer
     * to the same work (i.e. the same resource without regard to
     * version.)
     */
    public boolean sameWork(Object other) {
        if (other == null) return false;
        return getWorkSetStream()
            .anyMatch(g -> g.sameId(other));
    }

    /////////////////////////////////////////////////////////////////

    // Helper for toString() method; just produces part of the final string
    protected String _idsToString() {
        return _idByType.entrySet().stream()
            .map(entry -> entry.getValue().toString())
            .collect(Collectors.joining(", "));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
