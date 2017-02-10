package gov.ncbi.pmc.cite;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceprogram.kittycache.KittyCache;

import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

/**
 * This fetches item data in either PubOne or citeproc-json format, given an
 * IdSet. One of these is instantiated per servlet.
 */
public abstract class ItemSource {
    protected Logger log;

    // Implement a small-lightweight cache for the retrieved JSON items, to
    // support requests for multiple styles of the same id (for example)
    private KittyCache<String, JsonNode> jsonCache;
    private static final int jsonCacheSize = 100;
    private static final int jsonCacheTtl = 10;

    public ItemSource()
    {
        log = LoggerFactory.getLogger(this.getClass());
        jsonCache = new KittyCache<String, JsonNode>(jsonCacheSize);
    }

    /**
     * Every ItemSource prefers IDs of a particular type. This defaults to `aiid` (PMC
     * article instance id).
     * @return String - specifies the preferred ID for this item source.
     */
    public String wantsIdType() {
        return "aiid";
    }

    /**
     * Get the NXML for a given ID.
     */
    public abstract Document retrieveItemNxml(RequestId requestId)
        throws BadParamException, NotFoundException, IOException;

    /**
     * Get the PubOne XML, given an ID.  The default implementation of
     * this produces the PubOne by transforming the NXML.
     *
     * @throws IOException - if something goes wrong with the transformation
     */
    public Document retrieveItemPubOne(RequestId requestId)
        throws BadParamException, NotFoundException, IOException
    {
        Document nxml = retrieveItemNxml(requestId);
        // Prepare id parameters that get passed to the xslt
        Map<String, String> params = new HashMap<String, String>();
        Identifier pmid = requestId.getIdByType("pmid");
        if (pmid != null) params.put("pmid", pmid.getValue());
        Identifier pmcid = requestId.getIdByType("pmcid");
        if (pmcid != null) params.put("pmcid", pmcid.getValue());

        return (Document) App.doTransform(nxml, "pub-one", params);
    }

    /**
     * Get the item as a json object, as defined by citeproc-json. This
     * generates the JSON from the PubOne format, and then modifies the
     * results slightly, adding the id field.
     *
     * @throws IOException - if there's some problem retrieving the PubOne or
     *   transforming it
     */
    public JsonNode retrieveItemJson(RequestId requestId)
        throws BadParamException, NotFoundException, IOException
    {
        String curie = requestId.getIdByType(this.wantsIdType()).getCurie();
        JsonNode cached = jsonCache.get(curie);
        if (cached != null) {
            log.debug("JSON for " + curie + ": kitty-cache hit");
            return cached;
        }
        log.debug("JSON for " + curie + ": kitty-cache miss");

        Document pub_one = retrieveItemPubOne(requestId);

        // Add accessed as a param
        Map<String, String> params = new HashMap<String, String>();
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setTimeZone(tz);
        String nowAsISO = df.format(new Date());
        params.put("accessed", nowAsISO);

        String jsonStr =
            (String) App.doTransform(pub_one, "pub-one2json", params);
        ObjectNode json = (ObjectNode) App.getMapper().readTree(jsonStr);
        json.put("id", curie);
        jsonCache.put(curie, json, jsonCacheTtl);
        return json;
    }


    public ObjectMapper getMapper() {
        return App.getMapper();
    }}
