package gov.ncbi.pmc.cite;

import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Implementation of ItemSource that gets data from a single stcache.
 */
public class PubmedPubOneItemSource extends ItemSource {
    // This is the value of the item_source_loc system property, and should have
    // a literal `${id}` in the place where the pubmed id should go.
    private String urlTemplate;

    private final String USER_AGENT = "Mozilla/5.0";

    private Proxy proxy = Proxy.NO_PROXY;


    // This class has its own DocumentBuilderFactory, because it uses some
    // non-default settings
    private DocumentBuilderFactory dbf;

    public PubmedPubOneItemSource() throws Exception
    {
        super();
        urlTemplate = System.getProperty("item_source_loc");
        if (urlTemplate == null) throw new IOException(
            "Need a value for the item_source_loc system property");
        log.info("Item source location (pubmed pub-one image) = '" +
            urlTemplate + "'");

        proxy = initProxy(System.getProperty("proxy"));
        if (proxy != Proxy.NO_PROXY)
            log.info("Using proxy " + proxy.toString());

        dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            false);
    }

    /**
     * Instantiate a new Proxy object based on the host:port given to us. If
     * the argument is null, then Proxy.NO_PROXY is returned.
     */
    public static Proxy initProxy(String proxyUrl) {
        if (proxyUrl == null) return Proxy.NO_PROXY;

        String[] parts = proxyUrl.split(":");
        String host = parts[0];
        int port = parts.length == 1 ? 80 : Integer.parseInt(parts[1]);
        InetSocketAddress sock = new InetSocketAddress(host, port);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, sock);
        return proxy;
    }

    @Override
    public String wantedType() {
        return "pmid";
    }

    @Override
    public Document retrieveItemNxml(RequestId requestId)
        throws IOException
    {
        throw new IOException(
            "Using PubOne data source; can't retrieve NXML data");
    }

    public String byteToHex2(byte b) {
        int i = b >= 0 ? b : 256 + b;
        String hs = Integer.toHexString(i);
        return hs.length() < 2 ? "0" + hs : hs;
    }

    @Override
    public Document retrieveItemPubOne(RequestId requestId)
        throws NotFoundException, IOException
    {
        Identifier id = requestId.getId("pmid");
        if (id == null) {
            throw new NotFoundException("ID not found");
        }

        String idType = id.getType();

        try {
            String urlStr = urlTemplate.replace("${id}", id.getValue());
            URL url = new URL(urlStr);
            log.info("Getting pub-one data from " + urlStr);
            HttpURLConnection cn = (HttpURLConnection) url.openConnection(proxy);
            cn.setRequestMethod("GET");
            cn.setRequestProperty("User-Agent", USER_AGENT);
            int responseCode = cn.getResponseCode();
            if (responseCode != 200) {
                log.error("PubMed backend bad response; code = " + responseCode);
                throw new IOException("PubMed backend unable to find that ID");
            }
            BufferedReader rdr = new BufferedReader(new InputStreamReader(cn.getInputStream()));

            String inputLine;
            StringBuffer resultBuf = new StringBuffer();
            while ((inputLine = rdr.readLine()) != null) {
                resultBuf.append(inputLine);
            }
            rdr.close();

            String resultStr = resultBuf.toString();

            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(resultStr)));
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }

}
