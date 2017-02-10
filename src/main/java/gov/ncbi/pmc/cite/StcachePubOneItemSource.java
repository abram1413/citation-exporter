package gov.ncbi.pmc.cite;

import gov.ncbi.pmc.Pmfu;
import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Implementation of ItemSource that gets data from a single stcache.
 */
public class StcachePubOneItemSource extends ItemSource {
    private String pubOneImage;
    private Pmfu pubOneStcache;
    // This class has its own DocumentBuilderFactory, because it uses some
    // non-default settings
    private DocumentBuilderFactory dbf;

    public StcachePubOneItemSource(String imageLoc) throws Exception
    {
        super();
        pubOneImage = imageLoc;
      /*
        pubOneImage = System.getProperty("item_source_loc");
        if (pubOneImage == null) throw new IOException(
            "Need a value for the item_source_loc system property");
        log.info("Item source location (pub-one stcache image) = '" +
            pubOneImage + "'");
      */
        pubOneStcache = new Pmfu(pubOneImage);

        dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            false);
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
        Identifier id = requestId.getIdByType(this.wantsIdType());
        if (id == null) {
            throw new NotFoundException("Only supporting aiid's at this time");
        }
        String idType = id.getType();

        byte[] pubOneBytes;
        try {
            pubOneBytes = pubOneStcache.get(idType + "-" + id.getValue());
        }
        catch (Exception e) {
            throw new IOException(e);
        }

        if (pubOneBytes == null) {
            throw new NotFoundException(
                "Unable to retrieve PubOne data for " + id);
        }
        String resultStr;
        try {
            resultStr = new String(pubOneBytes, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new IOException(e);
        }

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(resultStr)));
        }
        catch (Exception e) {
            throw new IOException(e);
        }
    }
}
