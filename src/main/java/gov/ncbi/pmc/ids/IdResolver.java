package gov.ncbi.pmc.ids;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceprogram.kittycache.KittyCache;

import gov.ncbi.pmc.cite.BadParamException;
import gov.ncbi.pmc.cite.NotFoundException;
import gov.ncbi.pmc.cite.ServiceException;

/**
 * This class resolves IDs entered by the user, using the PMC ID Converter
 * API (http://www.ncbi.nlm.nih.gov/pmc/tools/id-converter-api/).  This allows
 * the user to give us IDs in any number of forms, and we can look up the data
 * by one of either aiid (article instance id) or (not implemented yet) pmid.
 *
 * The central method here is resolveIds(), which returns an RequestIdList
 * object, which is basically just a list of IdGlobs.
 *
 * It calls the PMC ID converter backend if it gets any type of ID other than
 * aiid or pmid.  It can be configured to cache those results.
 */
public class IdResolver {
    /**
     * If caching is enabled, the results returned from the external ID resolver
     * service are cached here.  The keys of this are all of the known CURIEs
     * that we see.
     */
    KittyCache<String, IdGlob> idGlobCache;

    ObjectMapper mapper = new ObjectMapper(); // create once, reuse

    /// Controlled by system property id_cache_ttl (integer in seconds)
    private int idCacheTtl;

    /// Controlled by system property id_converter_url
    private String idConverterUrl;

    /// Controlled by system property id_converter_params
    private String idConverterParams;

    /// Base URL to use for the ID converter.  Combination of idConverterUrl
    /// and idConverterParams
    private String idConverterBase;

    private Logger log = LoggerFactory.getLogger(IdResolver.class);

    public IdResolver() {
        // To cache or not to cache?
        String cacheIdsProp = System.getProperty("cache_ids");
        if (cacheIdsProp != null ? Boolean.parseBoolean(cacheIdsProp) : false) {
            String idCacheTtlProp = System.getProperty("id_cache_ttl");
            idCacheTtl = idCacheTtlProp != null ?
                    Integer.parseInt(idCacheTtlProp): 86400;

            // Create a new cache
            int idCacheSize = 50000;
            log.debug("Instantiating idGlobCache, size = " + idCacheSize +
                ", time-to-live = " + idCacheTtl);
            idGlobCache = new KittyCache<String, IdGlob>(50000);
        }

        String idConverterUrlProp = System.getProperty("id_converter_url");
        idConverterUrl = idConverterUrlProp != null ? idConverterUrlProp :
            "https://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/";
        String idConverterParamsProp =
                System.getProperty("id_converter_params");
        idConverterParams = idConverterParamsProp != null ?
            idConverterParamsProp :
            "showaiid=yes&format=json&tool=ctxp&" +
            "email=pubmedcentral@ncbi.nlm.nih.gov";

        idConverterBase = idConverterUrl + "?" + idConverterParams + "&";
    }


    /**
     * Resolves a comma-delimited list of IDs into a RequestIdList, that will
     * include the identifiers of the "wanted" types (by default, aiids).
     *
     * @param idStr - comma-delimited list of IDs, from the `ids` query string
     *   param.
     * @return an IdSet object, whose idType value is either "pmid" or "aiid".
     */
    public RequestIdList resolveIds(String idStr)
            throws BadParamException, ServiceException, NotFoundException
    {
        return resolveIds(idStr, null);
    }

    public RequestIdList resolveIds(String idStr, String idType)
            throws BadParamException, ServiceException, NotFoundException
    {
        return resolveIds(idStr, idType, "aiid");
    }

    public RequestIdList resolveIds(String idStr, String idType,
                                    String wantType)
            throws BadParamException, ServiceException, NotFoundException
    {
        return resolveIds(idStr, idType, new String[] {wantType});
    }

    /**
     * Resolves a comma-delimited list of IDs into a ResolvedIdList.
     *
     * @param idStr - comma-delimited list of IDs, from the `ids` query string
     *   param.
     * @param idType - optional ID type, from the `idtype` query-string
     *   parameter. If not null, it must be "pmcid", "pmid", "mid", "doi" or
     *   "aiid". If it is null, then we try to figure out the type from the
     *   pattern of the first id in the list.
     * @return a ResolvedIdList object.  Not all of the items in that list are
     *   necessarily resolved.
     */
    public RequestIdList resolveIds(String idStr, String idType,
                                    String[] wantTypes)
        throws BadParamException, ServiceException, NotFoundException
    {
        String[] originalIdsArray = idStr.split(",");
        RequestIdList idList = new RequestIdList();

        // If idType wasn't specified, then we infer it from the form of the
        // first id in the list
        if (idType == null) {
            idType = Identifier.matchIdType(originalIdsArray[0]);
        }

        // Canonicalize every ID in the list.  If it doesn't match the expected
        // pattern, throw an exception.
        for (int i = 0; i < originalIdsArray.length; ++i) {
            String oid = originalIdsArray[i];
            Identifier cid = new Identifier(idType, oid);
            RequestId requestId = new RequestId(oid, cid);
            idList.add(requestId);
        }

        // Go through each ID in the list, and compose the idsToResolve list.
        List<RequestId> idsToResolve = new ArrayList<RequestId>();
        int numReqIds = idList.size();
        for (int i = 0; i < numReqIds; ++i) {
            RequestId requestId = idList.get(i);
            Identifier cid = requestId.getCanonical();

            // Try to get it from the cache
            if (idGlobCache != null) {
                IdGlob idGlob = idGlobCache.get(cid.getCurie());
                if (idGlob != null) {
                    requestId.setIdGlob(idGlob);
                    continue;
                }
            }

            boolean isWanted = false;
            for (int j = 0; wantTypes != null && j < wantTypes.length; ++j) {
                if (idType.equals(wantTypes[j])) isWanted = true;
            }
            if (!isWanted) {
                idsToResolve.add(requestId);
            }
        }

        // If needed, call the ID resolver.
        if (idsToResolve.size() > 0) {
            // Create the URL.  If this is malformed, it must be because of
            // bad parameter values, therefore a bad request (right?)
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < idsToResolve.size(); ++i) {
                if (i != 0) sb.append(",");
                sb.append(idsToResolve.get(i).getCanonical().getValue());
            }
            String idString = sb.toString();
            URL url = null;
            try {
                url = new URL(idConverterBase + "idtype=" + idType + "&ids=" +
                              idString);
            }
            catch (MalformedURLException e) {
                throw new BadParamException(
                    "Parameters must have a problem; got malformed URL for " +
                    "upstream service '" + idConverterBase + "'");
            }

            log.debug("Invoking '" + url + "' to resolve ids");
            ObjectNode idconvResponse = null;
            try {
                idconvResponse = (ObjectNode) mapper.readTree(url);
            }
            catch (Exception e) {    // JsonProcessingException or IOException
                throw new ServiceException(
                    "Error processing service request to resolve IDs from '" +
                    url + "'");
            }

            String status = idconvResponse.get("status").asText();
            if (!status.equals("ok"))
                throw new ServiceException(
                    "Problem attempting to resolve ids from '" + url + "'");

            // In parsing the response, we'll create IdGlob objects as we go. We
            // have to then match them back to the idList:  if the CURIE
            // corresponding to the original id type matches something in the
            // idList, then replace the idList value with this new (more
            // complete, presumably) idGlob.
            ArrayNode records = (ArrayNode) idconvResponse.get("records");
            for (int rn = 0; rn < records.size(); ++rn) {
                ObjectNode record = (ObjectNode) records.get(rn);
                IdGlob parent = globbifyRecord(record, idType, idList);

                if (parent != null) {
                    // Now let's do the individual versions
                    ArrayNode versions = (ArrayNode) record.get("versions");
                    if (versions != null) {
                        for (int vn = 0; vn < versions.size(); ++vn) {
                            ObjectNode version = (ObjectNode) versions.get(vn);
                            IdGlob versionGlob =
                                globbifyRecord(version, idType, idList);
                            if (versionGlob != null)
                                parent.addVersion(versionGlob);
                        }
                    }
                }
            }
        }

        return idList;
    }

    /**
     * Helper function to create an IdGlob object out of a single JSON record
     * from the id converter. Once it does that, it matches it to the requested
     * ID in the idList, and inserts this new object.
     */
    private IdGlob globbifyRecord(ObjectNode record, String fromIdType,
                                  RequestIdList idList)
    {
      synchronized(this) {

        JsonNode status = record.get("status");
        if (status != null && status.asText() != "success") return null;

        IdGlob newGlob = new IdGlob();

        // Iterate over the other fields in the response record, and add
        // Identifiers to the glob
        Iterator<String> i = record.fieldNames();
        while (i.hasNext()) {
            String key = i.next();
            if (!key.equals("versions") &&
                !key.equals("current") &&
                !key.equals("live") &&
                !key.equals("status") &&
                !key.equals("errmsg") &&
                !key.equals("release-date"))
            {
                Identifier newId = null;
                try {
                    newId = new Identifier(key, record.get(key).asText());
                }
                catch (BadParamException e) {
                    // If the JSON has a field we don't recognize
                    System.out.println(
                        "Unrecognized field in ID converter JSON response: " +
                        record.get(key).asText());
                }

                if (newId != null) {
                    newGlob.addId(newId);
                    if (idGlobCache != null)
                        idGlobCache.put(newId.getCurie(), newGlob, idCacheTtl);
                }
            }
        }

        // If this new glob looks like one of the ones in the requested list,
        // then replace the value in the idList with this new, improved one
        Identifier fromId = newGlob.getIdByType(fromIdType);
        if (fromId != null) {
            int idListIndex = idList.lookup(fromId);
            if (idListIndex != -1) {
                idList.get(idListIndex).setIdGlob(newGlob);
            }
        }

        return newGlob;
      }
    }
}
