package gov.ncbi.pmc.cite;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.resolver.tools.CatalogResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.ncbi.pmc.ids.IdDb;
import gov.ncbi.pmc.ids.IdResolver;
import gov.ncbi.pmc.ids.IdType;
import net.sf.saxon.s9api.Processor;

/**
 * Container for some singleton-type objects that are instantiated and shared,
 * regardless of the context we're running in (i.e. webapp vs. unit test).
 */
public class App {
    public static final String apiVersion = "v1";

    private static String ctxp_version;
    private static String ctxp_sha;
    private static String ctxp_config_sha;

    private static Logger log = LoggerFactory.getLogger(App.class);
    private static IdDb idDb;
    private static IdResolver idResolver;
    // Jackson ObjectMapper should be thread-safe, see
    // http://wiki.fasterxml.com/JacksonFAQThreadSafety
    private static ObjectMapper mapper;

    // FIXME: this should go away
    private static DocumentBuilderFactory dbf;
    // to be replaced with
    private static Processor saxonProcessor;

    private static ItemSource itemSource;
    private static TransformEngine transformEngine;
    private static CatalogResolver catalogResolver;
    private static CiteprocPool citeprocPool;


    public static void init() throws Exception {
        Properties props = new Properties();
        props.load(
            App.class.getClassLoader().getResourceAsStream("ctxp.properties"));
        ctxp_version = props.getProperty("ctxp.version", "unknown");
        ctxp_sha = props.getProperty("ctxp.sha", "unknown");
        ctxp_config_sha = props.getProperty("ctxp.config.sha", "unknown");

        idDb = new IdDb();
        idResolver = new IdResolver();
        mapper = new ObjectMapper();
        saxonProcessor = new Processor(false);

        // Controlled by system property item_provider (default is "test")
        String itemSourceProp = System.getProperty("item_source");
        String itemSourceStr = itemSourceProp != null ? itemSourceProp : "test";
        log.info("Using item source '" + itemSourceStr + "'");

        if (itemSourceStr.equals("test")) {
            URL samples = App.class.getClassLoader().getResource("samples/");
            String wantsIdTypeProp = System.getProperty("item_source_id_type", "aiid");
            IdType wanted = idDb.getType(wantsIdTypeProp);
            itemSource = new TestItemSource(samples, wanted);
        }
        else {
            // Create a new item source by class name
            itemSource = (ItemSource) Class.forName(itemSourceStr)
                .getConstructor().newInstance();
        }

        transformEngine = new TransformEngine();
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        String xml_catalog_files = System.getProperty("xml.catalog.files");
        if (xml_catalog_files == null || xml_catalog_files.equals("")) {
            System.setProperty("xml.catalog.files", "catalog.xml");
        }
        log.info("Instantiating an XML catalog resolver, using " +
            "xml.catalog.files = " + System.getProperty("xml.catalog.files"));
        catalogResolver = new CatalogResolver();
        citeprocPool = new CiteprocPool(itemSource);
    }

    /**
     * @return the version number, from the pom.xml file.
     */
    public static String getCtxpVersion() {
        return ctxp_version;
    }

    /**
     * @return the Git SHA of this version of the citation-exporter repo, if
     *   available; otherwise, "unknown".
     */
    public static String getCtxpSha() {
        return ctxp_sha;
    }

    /**
     * @return the ctxp_config_sha the Git SHA of this version of the
     *   citation-exporter-config repo, if available; otherwise, "unknown".
     */
    public static String getCtxpConfigSha() {
        return ctxp_config_sha;
    }

    /**
     * Utility function for getting an XML DocumentBuilder that uses
     * catalogs
     */
    public static DocumentBuilder newDocumentBuilder()
        throws ParserConfigurationException
    {
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver(catalogResolver);
        return db;
    }

    /**
     * Get the ID database
     */
    public static IdDb getIdDb() {
        return idDb;
    }

    /**
     * Get the IdResolver
     */
    public static IdResolver getIdResolver() {
        return idResolver;
    }

    /**
     * Get the Jackson ObjectMapper
     */
    public static ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * Get the Saxon Processor, used for building XdmNode's for XML
     * resources
     */
    public static Processor getSaxonProcessor() {
        return saxonProcessor;
    }

    /**
     * Get the ItemSource
     */
    public static ItemSource getItemSource() {
        return itemSource;
    }

    /**
     * Get the TransformEngine
     */
    public static TransformEngine getTransformEngine() {
        return transformEngine;
    }

    /**
     * Convenience method that delegates to TransformEngine.
     */
    public static Object doTransform(Document src, String transform)
        throws IOException
    {
        return transformEngine.doTransform(src, transform);
    }

    /**
     * Convenience method that delegates to TransformEngine.
     */
    public static Object doTransform(Document src, String transform,
                                     Map<String, String> params)
        throws IOException
    {
        return transformEngine.doTransform(src, transform, params);
    }

    /**
     * Get the CiteprocPool object
     */
    public static CiteprocPool getCiteprocPool() {
        return citeprocPool;
    }

}
