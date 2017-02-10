package gov.ncbi.pmc.ids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Instances of this class store a list of identifiers that correspond to some
 * real-world resource, like a journal article. There are two types:
 * * non-versioned - correspond roughly to a FRBR "work", for example the ID
 *   "PMC3159421" does not refer to any particular version.  Non-versioned
 *   globs might have a list of versioned glob "children".
 * * versioned - corresponds roughly to a FRBR "expression", for example, the
 *   ID "PMC3159421.1". Every versioned glob will have a reference to its
 *   parent non-versioned glob
 */
public class IdGlob {
    // Cross reference from an id-type (CURIE prefix) to Identifier object
    private Map<String, Identifier> idByType = null;

    // If there are different versions associated with this ID, then
    // versionKids will not be null.
    private List<IdGlob> versionKids = null;

    // If this is an individual version of a work, then this will point to
    // the non-versioned IdGlob
    private IdGlob parent = null;

    public IdGlob() {
        idByType = new HashMap<>();
    }

    public void addId(Identifier newId) {
        idByType.put(newId.getType(), newId);
    }

    public void addVersion(IdGlob versionGlob) {
        if (versionKids == null) versionKids = new ArrayList<IdGlob>();
        versionKids.add(versionGlob);
        versionGlob.parent = this;
    }

    public boolean hasType(String type) {
        return getIdByType(type) != null;
    }

    /**
     * Get an Identifier from this glob, given the type.  Note that this
     * takes versions into account.  If this IdGlob has a parent (meaning
     * that this is a version-specific IdGlob, and it doesn't have the
     * requested type, but it's parent does, then the parent's value is
     * returned.
     */
    public Identifier getIdByType(String type) {
        Identifier id = idByType.get(type);
        if (id == null && parent != null) id = parent.getIdByType(type);
        return id;
    }

    public boolean isVersioned() {
        return parent != null;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Identifier> e: idByType.entrySet()) {
            if (sb.length() != 0) sb.append(",");
            sb.append(e.getValue().getCurie());
        }
        return sb.toString();
    }
}
