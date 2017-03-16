package gov.ncbi.pmc.cite;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.undercouch.citeproc.CSL;
import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.helper.json.JsonLexer;
import de.undercouch.citeproc.helper.json.JsonParser;
import de.undercouch.citeproc.output.Bibliography;
import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

/**
 * This is a wrapper for a pair of citeproc-java objects: one CSL and one
 * ItemDataProvider. That pair of objects collaborate to generate a
 * bibliography in a particular style for a set of citations.
 *
 * Each object is style-specific:  it can only format citations in one given
 * style. They are not thread safe, so they are managed by a CiteprocPool
 * object.
 */
public class CitationProcessor {
    protected Logger log;
    private CSL csl;
    private final String style;
    private ItemSource itemSource;
    private ItemProvider itemProvider;
    private String wantsIdType;

    /**
     * @param style
     * @param itemSource
     * @throws IOException - when there's an error creating the CSL object.
     */
    CitationProcessor(String style, ItemSource itemSource)
        throws NotFoundException
    {
        log = LoggerFactory.getLogger(this.getClass());
        this.style = style;
        this.itemSource = itemSource;
        this.wantsIdType = itemSource.wantedType();
        itemProvider = new ItemProvider();
        try {
            csl = new CSL(itemProvider, style);
        }
        catch (IOException e) {
            throw new NotFoundException("Style '" + style + "' not found", e);
        }
    }

    public String getStyle() {
        return style;
    }

    /**
     * Make a bibliography in the "html" format.
     */
    public Bibliography makeBibliography(RequestIdList idList)
            throws NotFoundException, BadParamException, IOException
    {
        return makeBibliography(idList, "html");
    }

    /**
     * Make a bibliography.
     * @param idList
     * @param format - one of "html", "text", "asciidoc", "fo", or "rtf".
     * (See the citeproc-java API docs)
     */
    public Bibliography makeBibliography(RequestIdList idList, String format)
        throws NotFoundException, BadParamException, IOException
    {
        log.debug("makeBibliography: calling prefetchItems() with idList:" + idList.toString());
        prefetchItems(idList);
        log.debug("back from prefetchItems()");
        csl.setOutputFormat(format);

        log.debug("Calling idList.getCuriesByType(" + this.wantsIdType + ")");
        String [] curies = idList.getCuriesByType(this.wantsIdType);
        log.debug("Got curies: " + String.join(",", curies));

        csl.registerCitationItems(curies);
        long mb_start = System.currentTimeMillis();
        Bibliography bibl = csl.makeBibliography();
        log.debug("makeBibliography took " +
            (System.currentTimeMillis() - mb_start) + " milliseconds");
        if (bibl == null) {
            throw new IOException(
                "Bad request, problem with citation processor");
        }
        return bibl;
    }

    /**
     * Pre-fetch the JSON, and construct a CSLItemData object for a set of IDs
     * that we're interested in (per request).  This allows us to respond with
     * an informative error message, if there's a problem.  Otherwise, the
     * retrieveItem() method (below) is called from within citeproc-js, and
     * there's no way to pass the error message back out.
     *
     * @param idList
     * @throws IOException - for error in Jackson serialization, etc.
     */
    public void prefetchItems(RequestIdList idList)
        throws NotFoundException, BadParamException, IOException
    {
        log.debug(">>>>>>>>>>>>>>>>>>>>>>>>>>>> prefetchItems <<<<<<<<<<<<<<<<<<<<<<<<<< ");
        itemProvider.clearCache();
        int numIds = idList.size();

        // Keep track of whether or not we find at least one good result
        boolean foundOneGood = false;
        Exception e = null;
        for (int i = 0; i < numIds; ++i) {
            RequestId requestId = idList.get(i);
            Identifier id = requestId.getId(this.wantsIdType);
            log.debug("id = " + id);
            if (id == null) continue;
            String curie = id.getCurie();
            log.debug("curie = " + curie);

            // Unfortunately, we have to get the JSON as a Jackson object, then
            // serialize it and parse it back in as a citeproc-java object. See
            // this question:
            // https://github.com/michel-kraemer/citeproc-java/issues/9

            // Use the data field of the requestId to store the status of
            // generating the JSON, where `false` means failed and `true` means
            // succeeded.

            requestId.setData(false);  // assume this will fail
            ObjectMapper objectMapper = itemSource.getMapper();
            JsonNode jsonNode = null;
            try {
                jsonNode = itemSource.retrieveItemJson(requestId);
            }
            catch (IOException ioe) {
                log.info("Got IOException attempting to get JSON for " +
                    requestId + ": " + ioe);
                e = ioe;
            }
            catch (CiteException ce) {
                e = ce;
            }
            if (jsonNode != null) {
                String jsonString = null;
                try {
                    jsonString = objectMapper.writeValueAsString(jsonNode);
                }
                catch (JsonProcessingException jpe) {
                    // An error in Jackson's serialization of known-good json
                    throw new IOException(jpe);
                }
                JsonLexer jsonLexer =
                    new JsonLexer(new StringReader(jsonString));
                JsonParser jsonParser = new JsonParser(jsonLexer);
                Map<String, Object> itemJsonMap =  jsonParser.parseObject();

                // Add the id key-value pair
                itemJsonMap.put("id", curie);
                CSLItemData item = CSLItemData.fromJson(itemJsonMap);
                if (item == null) throw new IOException(
                    "Problem creating a CSLItemData object from backend JSON");
                itemProvider.addItem(curie, item);
                requestId.setData(true); // success
                foundOneGood = true;
            }
        }

        // If the total number of good CSL items we've found is zero, throw an
        // exception
        if (!foundOneGood) {
            String msg = "Couldn't retrieve/create any good CSL items for " +
                "this ID list";
            if (e == null) throw new NotFoundException(msg);
            else throw new NotFoundException(msg, e);
        }
    }

    public CSL getCsl() {
        return csl;
    }
}
