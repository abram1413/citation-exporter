package gov.ncbi.pmc.cite;

import java.io.IOException;
import java.net.URL;

import org.w3c.dom.Document;

import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

/**
 * This item sources uses the PMC NXML converter app (internal to NCBI),
 * which provides (as the name implies) NXML of the article, which is then
 * converted into PubOne on the fly.
 */
public class ConvAppNxmlItemSource  extends ItemSource {
    private URL convAppUrl;

    public ConvAppNxmlItemSource(URL url) throws Exception
    {
        super();
        convAppUrl = url;
      /*
        convAppUrl = new URL(System.getProperty("item_source_loc"));
        if (convAppUrl == null) throw new IOException(
            "Need a value for the item_source_loc system property");
        log.info("Item source location (nxml converter app URL) = '" +
            convAppUrl + "'");
      */
    }

    @Override
    public Document retrieveItemNxml(RequestId requestId)
        throws BadParamException, NotFoundException, IOException
    {
        Identifier id = requestId.getIdByType(this.wantsIdType());
        if (id == null)
            throw new BadParamException("No id of type aiid in " + requestId);

        URL nxmlUrl = new URL(convAppUrl, id.getValue());
        log.debug("Reading NXML from " + nxmlUrl);
        Document nxml = null;

        try {
            nxml = App.newDocumentBuilder().parse(
                nxmlUrl.openStream()
            );
        }
        catch (Exception e) {
            throw new IOException(e);
        }

        if (nxml == null) {
            throw new NotFoundException("Failed to read NXML from " + nxmlUrl);
        }

        return nxml;
    }
}
