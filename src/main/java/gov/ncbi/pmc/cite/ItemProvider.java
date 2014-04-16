package gov.ncbi.pmc.cite;

import java.util.HashMap;
import java.util.Map;

import de.undercouch.citeproc.ItemDataProvider;
import de.undercouch.citeproc.csl.CSLItemData;

public class ItemProvider implements ItemDataProvider
{
    // Stores CSLItemData objects between the time that they are prefetched and the time that they
    // are used by the citeproc-js code.
    protected Map<String, CSLItemData> cslItemCache;

    public ItemProvider() {
    }

    /**
     * This is called at the start of a request to initialize the temporary
     * storage for the csl item data.
     */
    public void clearCache() {
        cslItemCache = new HashMap<String, CSLItemData>();
    }
    /**
     * Add a prefetched item to the cache
     */
    public void addItem(String tid, CSLItemData item) {
        cslItemCache.put(tid, item);
    }

    /**
     * Retrieve a CSLItemData object, given an id.  This is invoked by the citeproc-js code
     * running inside Rhino.
     * @return the CSLItemData object corresponding to this id, or null if not found.
     */
    public CSLItemData retrieveItem(String tid)
    {
        CSLItemData result = cslItemCache.get(tid);
        //System.out.println("retrieveItem, typeAndId = " + tid + ", found '" + result + "'");
        return result;
    }

    // FIXME:  What is this used for?
    public String[] getIds() {
        String ids[] = { "PMC3362639" };
        return ids;
    }

}
