package gov.ncbi.pmc.cite;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import de.undercouch.citeproc.output.Bibliography;
import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

/**
 * Stores information about, and handles, a single request.
 */
public class Request {
    private HttpServletRequest request;
    private HttpServletResponse response;

    // NCBI-specific, see below.  (Note: I don't know what 'caf' stands for.)
    private LinkedList<String> cafPath;
    // Reverse-proxy path
    private LinkedList<String> reverseProxyPath;
    // Servlet context path
    private LinkedList<String> contextPath;
    // The original path given in the URL, as seen by the server
    private LinkedList<String> origPath;
    // If a path segment was given corresponding to the version, store it here
    private String versionSeg;
    // The portion of the path specific to the resource; after caf, context, and
    // version
    private LinkedList<String> resourcePath;
    // The base part of the path that is prepended to all links back to this
    // service
    private LinkedList<String> scriptPath;


    // This gets initialized by initPage(), when we know we're ready
    private PrintWriter page;

    // Data from query string params
    private RequestIdList idList;
    private String report;
    private String format;
    private String[] styles = {"modern-language-association"};  // default style

    // One document builder shared within this request thread. This is created
    // on-demand by getDocumentBuilder().
    private DocumentBuilder documentBuilder;

    private Logger log = LoggerFactory.getLogger(Request.class);

    private static final String searchNS =
            "http://www.ncbi.nlm.nih.gov/ns/search";

    /**
     * Constructor.
     */
    public Request(HttpServletRequest request, HttpServletResponse response)
        throws MalformedURLException
    {
        this.request = request;
        this.response = response;
        parsePath();
    }

    /**
     * Get the request object
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * Get the request object
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * This takes a String as input that represents a part of the path, and
     * returns a LinkedList of path segments.  The input might be null or the
     * empty string. Leading and trailing slashes are first removed, duplicate
     * slashes are normalized, and then the remaining segments are parsed out.
     */
    private LinkedList<String> parsePathSegs(String p) {
        LinkedList<String> segs = new LinkedList<String>();
        if (p != null) {
            String normP = p.replaceFirst("^/*(.*?)/*$", "$1");
            if (!normP.equals("")) {
                segs.addAll(Arrays.asList(normP.split("/+")));
            }
        }
        return segs;
    }

    /**
     * For debugging, this lets us print out a list of path segments
     */
    @SuppressWarnings("unused")
    private String printPathSegs(LinkedList<String> ps) {
        return "'" + pathSegsToString(ps) + "'";
    }

    /**
     * Convert a list of path segments into a string
     */
    public String pathSegsToString(LinkedList<String> ps) {
        return StringUtils.join(ps.toArray(), "/");
    }

    /**
     * Called from the constructor, this dissects the path part of the request
     * URL, and initializes scriptPath and resourcePath.
     */
    private void parsePath()
        throws MalformedURLException
    {
        // NCBI-specific:  If we are being proxied through a web front, then
        // the `CAF-Url` header will be set.  This needs to be added back to
        // outgoing links
        String cafUrl = request.getHeader("CAF-Url");
        if (cafUrl == null) {
            cafPath = new LinkedList<String>();
        }
        else {
            URL url = new URL(cafUrl);
            cafPath = parsePathSegs(url.getPath());
        }

        // Servlet context path, if there is one
        contextPath = parsePathSegs(request.getContextPath());

        // This will be the part of the URL path after contextPath
        origPath = parsePathSegs(request.getPathInfo());

        // Determine the reverse-proxy path.  This is the portion of the CAF
        // URL preceding the contextPath and the origPath
        reverseProxyPath = new LinkedList<String>();
        int numSegs = cafPath.size() - contextPath.size() - origPath.size();
        if (numSegs > 0) reverseProxyPath.addAll(cafPath.subList(0, numSegs));

        // Remove (our) version number, if it is the first path segment
        resourcePath = new LinkedList<String>();
        if (origPath.size() >= 1 && origPath.get(0).equals(App.apiVersion)) {
            versionSeg = App.apiVersion;
            resourcePath.addAll(origPath.subList(1, origPath.size()));
        }
        else resourcePath.addAll(origPath);

        scriptPath = new LinkedList<String>();
        scriptPath.addAll(reverseProxyPath);
        scriptPath.addAll(contextPath);
        if (versionSeg != null) scriptPath.add(versionSeg);
    }

    public String getScriptPath() {
        return pathSegsToString(scriptPath);
    }

    public String getOrigPath() {
        return pathSegsToString(origPath);
    }

    /**
     * Returns true if no (meaningful) path part is present in the URL.
     */
    public boolean pathEmpty() {
        return resourcePath.size() == 0;
    }

    /**
     * Returns true if the path in the request (after any context and version
     * part) has only one segment, and its value matches that given.
     */
    public boolean pathEquals(String expectSeg) {
        return resourcePath.size() == 1 &&
               resourcePath.get(0).equals(expectSeg);
    }

    /**
     * Returns true if the path in the request (after any context and version
     * part) matches the list of segments given.
     */
    public boolean pathEquals(String[] expectSegs) {
        boolean eq = false;
        if (expectSegs.length == resourcePath.size()) {
            eq = true;
            int numSegs = expectSegs.length;
            for (int i = 0; i < numSegs; ++i) {
                if (!expectSegs[i].equals(resourcePath.get(i))) {
                    eq = false;
                    break;
                }
            }
        }
        return eq;
    }

    /**
     * Process a GET request.
     */
    public void doGet()
        throws ServletException
    {
        try {
            log.debug("Got a new GET request");

            // First attempt to resolve the IDs into an IdSet, which contains
            // the id type and each of the IDs in a canonicalized form.
            String idsParam = request.getParameter("ids");
            String idParam = request.getParameter("id");
            if (idsParam != null && idParam != null) {
                errorResponse(
                    "Both `ids` and `id` parameter were set in the request",
                    400);
                return;
            }
            if (idsParam == null && idParam == null) {
                errorResponse("Need to specify at least one ID", 400);
                return;
            }

            String idp = idsParam != null ? idsParam : idParam;

            // The IdResolver seems to be thread-safe
            idList = App.getIdResolver().resolveIds(idp,
                request.getParameter("idtype"), new String[] {"aiid"});

            // Right now, we only support getting the record by aiid.
            // Later, we will want to add pmid
            log.debug("Resolved ids: " + idList);
            if (idList.numHasType("aiid") == 0)
                throw new BadParamException("No resolvable IDs found: " +
                    idList);

            // FIXME:  this should be data-driven
            // Get report and format, validating and implementing defaults.
            report = request.getParameter("report");
            if (report == null) { report = "html"; }
            format = request.getParameter("format");
            if (format == null) {
                if (report.equals("html"))
                    format = "html";
              /* FIXME: rtf format not implemented yet.
                else if (report.equals("rtf"))
                    format = "rtf";
              */
                else if (report.equals("ris"))
                    format = "ris";
                else if (report.equals("nbib"))
                    format = "nbib";
                else if (report.equals("citeproc"))
                    format = "json";
                else if (report.equals("pub-one") || report.equals("pub1"))
                    format = "xml";
                else if (report.equals("nxml"))
                    format = "xml";
            }

            if (report.equals("html") && format.equals("html")) {
                styledCitation();
            }

            else if (report.equals("citeproc") && format.equals("json")) {
                citeprocJson();
            }

            else if ((report.equals("pub-one") || report.equals("pub1")) &&
                    format.equals("xml")) {
                pubOneXml();
            }

            else if (report.equals("nxml") && format.equals("xml")) {
                nXml();
            }

            else if (report.equals("nbib") && format.equals("nbib") ||
                     report.equals("ris") && format.equals("ris"))
            {
                transformXml(report);
            }

            else {
                errorResponse("Not sure what I'm supposed to do. Check the " +
                    "values of report and format.", 400);
                return;
            }
        }
        catch (NotFoundException e) {
            log.error("NotFoundException", e);
            errorResponse(e.getMessage(), 404);
            return;
        }
        catch (ServiceException e) {
            log.error("ServiceException", e);
            errorResponse(e.getMessage(), 500);
            return;
        }
        catch (BadParamException e) {
            log.error("BadParamException", e);
            errorResponse(e.getMessage(), 400);
            return;
        }
        catch (IOException e) {
            log.error("IOException", e);
            errorResponse(e.getMessage(), 500);
            return;
        }
    }

    /**
     * Respond to the client with a PubOne document.
     */
    private void pubOneXml()
        throws NotFoundException, BadParamException, IOException
    {
        ItemSource itemSource = App.getItemSource();

        int numIds = idList.size();
        log.debug("Getting PubOne for ids " + idList);

        String pubOneString;  // response goes here
        if (numIds == 1) {
            RequestId requestId = idList.get(0);
            Document d = itemSource.retrieveItemPubOne(requestId);
            pubOneString = serializeXml(d);
        }
        else {
            // Create a new XML document which will wrap (aggregate) all
            // the individual records' XML documents.
            Document d = getDocumentBuilder().newDocument();
            Element root = d.createElement("pub-one-records");
            d.appendChild(root);

            List<Identifier> notFoundList = new ArrayList<Identifier>();
            for (int i = 0; i < numIds; ++i) {
                boolean success = false;
                RequestId requestId = idList.get(i);
                Identifier rid = requestId.getId();
                Identifier aiid = requestId.getId("aiid");
                if (aiid != null) {
                    try {
                        // Retrieve the PubOne record XML
                        Document record =
                            itemSource.retrieveItemPubOne(requestId);

                        // Add an `s:request-id` attribute with the original
                        // (requested) id
                        Element recordElem = record.getDocumentElement();
                        setSearchAttribute(recordElem, "request-id",
                            rid.getCurie());

                        // If that's different from the aiid, then add
                        // another attribute, `s:resolved-id`
                        if (!rid.equals(aiid)) {
                            setSearchAttribute(recordElem, "resolved-id",
                                aiid.getCurie());
                        }

                        // Append the root element of this record's XML
                        // document as the last child of the root element
                        // of our aggregate document.
                        root.appendChild(d.importNode(recordElem, true));
                        success = true;
                    }
                    catch (CiteException e) {}
                }
                if (!success) notFoundList.add(rid);
            }

            // If there were any IDs not found, add an attribute to the
            // record-set element
            if (notFoundList.size() > 0) {
                setSearchAttribute(root, "not-found",
                    StringUtils.join(notFoundList, " "));
            }
            pubOneString = serializeXml(d);
        }

        response.setContentType("application/xml;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        initPage();
        if (page == null) return;
        page.print(pubOneString);
    }

    /**
     * Utility function to set a search-namespaced attribute on an Element
     */
    private void setSearchAttribute(Element elem, String attrName,
                                    String attrValue)
    {
        Document doc = elem.getOwnerDocument();
        Attr attr = doc.createAttributeNS(searchNS, "s:" + attrName);
        attr.setValue(attrValue);
        elem.setAttributeNodeNS(attr);
    }

    /**
     * Respond to the client with an NXML document.  This is only available
     * for some of the item sources, and is not an official part of the
     * api/service.
     */
    private void nXml()
        throws BadParamException, NotFoundException, IOException
    {
        ItemSource itemSource = App.getItemSource();
        //String idType = idSet.getType();
        int numIds = idList.size();

        Document d = null;
        if (numIds == 1) {
            RequestId requestId = idList.get(0);
            //IdGlob idg = requestId.getIdGlob();
            if (requestId == null)
                throw new BadParamException("ID was not properly resolved");
            d = itemSource.retrieveItemNxml(requestId);
        }
        else {
            d = getDocumentBuilder().newDocument();
            Element root = d.createElement("records");
            d.appendChild(root);

            for (int i = 0; i < numIds; ++i) {
                RequestId requestId = idList.get(i);
                //IdGlob idg = requestId.getIdGlob();
                if (requestId != null) {
                    Document record = itemSource.retrieveItemNxml(requestId);
                    root.appendChild(d.importNode(record.getDocumentElement(),
                         true));
                }
            }
        }

        String xmlStr = serializeXml(d);

        response.setContentType("application/xml;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        initPage();
        if (page == null) return;
        page.print(xmlStr);
    }

    /**
     * Utility function to serialize an XML object for output back to the
     * client.
     */
    private static String serializeXml(Document doc, boolean omitXmlDecl)
        throws IOException
    {
        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            Transformer transformer = tf.newTransformer();
            if (omitXmlDecl) {
                transformer.setOutputProperty("omit-xml-declaration", "yes");
            }
            transformer.transform(domSource, result);
        }
        catch (TransformerException ex) {
            throw new IOException(ex);
        }
        writer.flush();
        return writer.toString();
    }

    /**
     * Same as above, but omitXmlDecl defaults to false
     */
    private static String serializeXml(Document doc)
        throws IOException
    {
        return serializeXml(doc, false);
    }

    /**
     * Respond to the client with a document that is the result of running
     * the PubOne through an XSLT transformation.
     */
    private void transformXml(String report)
        throws NotFoundException, BadParamException, IOException
    {
        // FIXME:  this all has to be data-driven.
        // That means:  the content-type of the output, the XSLT to use, and,
        // a function to use to handle concatenation of multiple records.
        int numIds = idList.size();
        ItemSource itemSource = App.getItemSource();
        TransformEngine transformEngine = App.getTransformEngine();

        String transformName =
                report.equals("ris") ? "pub-one2ris" :
                report.equals("nbib") ? "pub-one2medline" :
                report;
        String contentDispHeader;
        String result = "";
        if (numIds == 1) {
            RequestId requestId = idList.get(0);
            if (requestId == null)
                throw new BadParamException("ID was not properly resolved");
            Identifier id = requestId.getId(
                new String[]{"pmcid", "aiid"});
            String outFilename = id.getType() + "-" + id.getValue() + "." +
                report;
            contentDispHeader = "attachment; filename=" + outFilename;
            Document d = itemSource.retrieveItemPubOne(requestId);
            result = (String) transformEngine.doTransform(d, transformName);
        }
        else {
            contentDispHeader = "attachment; filename=results." + report;
            StringBuffer sb = new StringBuffer();
            CiteException lastException = null;
            for (int i = 0; i < numIds; ++i) {
                RequestId requestId = idList.get(i);
                if (requestId != null) {
                    try {
                        Document d = itemSource.retrieveItemPubOne(requestId);
                        String ris = (String) transformEngine
                            .doTransform(d, transformName);
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(ris);
                    }
                    catch (CiteException e) {
                        lastException = e;
                    }
                }
            }
            if (sb.length() == 0) throw new NotFoundException(
                "No good IDs found", lastException);
            result = sb.toString();
        }

        String contentType = report.equals("nbib") ? "application/nbib" :
            report.equals("ris") ? "application/x-research-info-systems" :
            "application/xml";
        response.setContentType(contentType + ";charset=UTF-8");
        response.setHeader("Content-disposition", contentDispHeader);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        initPage();
        if (page == null) return;
        page.print(result);
    }

    private void citeprocJson()
        throws NotFoundException, BadParamException, IOException
    {
        ItemSource itemSource = App.getItemSource();
        int numIds = idList.size();
        log.debug("Getting citeproc-json for ids " + idList);

        String jsonString;
        try {
            if (numIds == 1) {
                RequestId requestId = idList.get(0);
                //IdGlob idg = requestId.getIdGlob();
                if (requestId == null)
                    throw new BadParamException("ID was not properly resolved");

                JsonNode jn = itemSource.retrieveItemJson(requestId);
                jsonString = App.getMapper().writeValueAsString(jn);
            }
            else {
                List<String> jsonRecords = new ArrayList<String>();
                List<Identifier> notFoundList = new ArrayList<Identifier>();
                for (int i = 0; i < numIds; ++i) {
                    boolean success = false;

                    RequestId requestId = idList.get(i);
                    Identifier rid = requestId.getId();
                    Identifier aiid = requestId.getId("aiid");
                    if (aiid != null) {
                        try {
                            // Retrieve the JSON item and add it to our list
                            jsonRecords.add(
                                 itemSource.retrieveItemJson(requestId)
                                   .toString());
                            success = true;
                        }
                        catch (CiteException e) {}
                    }
                    if (!success) notFoundList.add(rid);
                }

                // Now construct the aggregated JSON array
                // FIXME:  I really should be using the Jackson library methods
                // to construct this. Right now there's no guarantee that the
                // not-found-list of IDs will be well-formed.
                // FIXME:  If there were no good records retrieved, throw an
                // exception.  Same goes for the PubOne and other formats.
                jsonString = "[";
                if (notFoundList.size() > 0) {
                    jsonString += "{ \"not-found\": \"" +
                        StringUtils.join(notFoundList, " ") + "\" },";
                }
                jsonString += StringUtils.join(jsonRecords, ", ");
                jsonString += "]";
            }
        }
        catch (JsonProcessingException e) {
            throw new IOException(e);
        }
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        initPage();
        if (page == null) return;
        page.print(jsonString);
    }

    /**
     * Respond to the client with a list of styled citations.
     */
    private void styledCitation()
        throws BadParamException, NotFoundException, IOException
    {
        String stylesParam = request.getParameter("styles");
        String styleParam = request.getParameter("style");
        if (stylesParam != null && styleParam != null) {
            throw new BadParamException(
                "Both `styles` and `style` parameter were set in the request");
        }
        String sp = stylesParam != null ? stylesParam : styleParam;
        if (sp != null) {
            log.debug("styles = " + StringUtils.join(styles, ", "));
            styles = sp.split(",");
        }

        int numIds = idList.size();
        if (numIds > 1 && styles.length > 1) {
            throw new BadParamException(
                "Sorry, I can do multiple records (ids) or multiple styles, " +
                "but not both.");
        }

        // Create a new XML document which will wrap the individual
        // bibliographies.
        Document entriesDoc = getDocumentBuilder().newDocument();
        Element entriesDiv = entriesDoc.createElement("div");
        entriesDoc.appendChild(entriesDiv);

        // For each style
        for (int styleNum = 0; styleNum < styles.length; ++styleNum) {
            String style = styles[styleNum];

            // Generate the bibliography (array of styled citations). Note
            // that the order that comes out might not be the same as the
            // order that goes in, so we'll put them back in the right order.
            Bibliography bibl = null;
            CiteprocPool cpPool = App.getCiteprocPool();

            // See PMC-21297.  We've noticed that sometimes the CSL
            // processors get into a bad state. So the code here will try
            // up to two processors.  If the first one gives an error, then
            // throw it away and try a second one.  If the second one gives
            // an error, then we'll assume it's a real error, and return it
            // to the pool.
            for (int cpNum = 0; cpNum < 2; cpNum++) {
                CitationProcessor cp = cpPool.getCiteproc(style);
                try {
                    bibl = cp.makeBibliography(idList, "html");
                }
                catch (RuntimeException e) {
                    log.error(
                        "Got a RuntimeException from citation processor: ", e);
                    // For runtime exceptions, if this is our first attempt,
                    // then we'll assume there's something wrong with the
                    // processor, so don't return it to the pool.
                    throw e;
                }
            }

            // Parse the output entries, and stick them into the output document
            String entryIds[] = bibl.getEntryIds();
            log.debug("entryIds: " + String.join(", ", entryIds));
            String entries[] = bibl.getEntries();


            // The array of ids that we will be outputting -- all the ones
            // that returned good data
            List<RequestId> goodIds = new ArrayList<RequestId>();
            int numRequestIds = idList.size();
            for (int i = 0; i < numRequestIds; ++i) {
                RequestId requestId = idList.get(i);
                //IdGlob idg = requestId.getIdGlob();
                if (requestId != null) goodIds.add(requestId);
            }

            int numGoodIds = goodIds.size();
            log.debug("numRequestIds = " + numRequestIds + ", numGoodIds = " + numGoodIds);
            String wantedType = App.getItemSource().wantedType();

            // FIXME: This is really ugly. I need to add a method to either Identifier or
            // IdGlob that compares it against any other Identifier or IdGlob. That method
            // should be able to recognize that IDs of two different types are equal.
            for (int idnum = 0; idnum < numGoodIds; ++idnum) {
                RequestId requestId = goodIds.get(idnum);
                log.debug("requestId = " + requestId);

                Identifier wid = requestId.getId(wantedType);
                log.debug("wid = " + wid);
                if (wid == null) continue;
                String curie = wid.getCurie();
                log.debug("curie = " + curie);
                int entryNum = ArrayUtils.indexOf(entryIds, curie);
                String entry = entries[entryNum];

                StringReader stringReader = new StringReader(entry);
                InputSource inputSource = new InputSource(stringReader);
                Document doc = null;
                try {
                    doc = getDocumentBuilder().parse(inputSource);
                }
                catch (SAXException e) {
                    throw new IOException("Problem interpreting " +
                        "citeproc-generated bibliography entry: " +
                        e.getMessage()
                    );
                }
                Element entryDiv = doc.getDocumentElement();
                entryDiv.setAttribute("data-style", style);
                Identifier rid = requestId.getId();
                entryDiv.setAttribute("data-id", rid.getCurie());
                if (!rid.equals(wid)) {
                    entryDiv.setAttribute("data-resolved-id", wid.getCurie());
                }

                // Remove the <div class='csl-left-margin'>, if it exists
                // (see PMC-21029)
                NodeList divKids = entryDiv.getElementsByTagName("div");
                int numKids = divKids.getLength();
                for (int i = 0; i < numKids; ++i) {
                    Node kid = divKids.item(i);
                    if (kid.getNodeType() == Node.ELEMENT_NODE &&
                        ((Element) kid).getAttribute("class").equals(
                                "csl-left-margin"))
                    {
                        entryDiv.removeChild(kid);
                        break;  // there can be only one.
                    }
                }

                // Add this entry to the wrapper
                entriesDiv.appendChild(entriesDoc.importNode(entryDiv, true));
            }
        }
        String s = serializeXml(entriesDoc, true);

        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        initPage();
        if (page == null) return;
        page.print(s);
    }

    private void errorResponse(String msg, int status)
    {
        log.info("Sending error response: " + msg);
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);
        initPage();
        if (page == null) return;
        page.println(msg);
    }

    /**
     * This helper function gets a PrintWriter to write the response to the
     * output. If it fails, from an IOException, it prints a message to the
     * log, and page will remain null.
     */
    private void initPage() {
        try {
            page = response.getWriter();
        }
        catch (IOException e) {
            log.error("Got IOException while trying to initialize HTTP " +
                "response page: " + e.getMessage());
            page = null;
        }
    }

    /**
     * At most one DocumentBuilder will be created per request.  Use this
     * function to create/access it.
     */
    private DocumentBuilder getDocumentBuilder()
        throws IOException
    {
        if (documentBuilder == null) {
            try {
                documentBuilder = App.newDocumentBuilder();
            }
            catch (ParserConfigurationException e) {
                throw new IOException(
                    "Problem creating a Saxon DocumentBuilder: " + e);
            }
        }
        return documentBuilder;
    }
}
