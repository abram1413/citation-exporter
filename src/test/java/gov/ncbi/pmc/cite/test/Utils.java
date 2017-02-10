package gov.ncbi.pmc.cite.test;

import java.io.StringWriter;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import gov.ncbi.pmc.cite.App;

/**
 * This provides a single function that does setup that is shared
 * by all the tests.
 */
public class Utils {

    /**
     * Call this first from your test method, and it will log the test name
     * first, and then initialize the App. Use it to set the test's log
     * variable at the same time. For example:
     *
     * <pre><code>public class MyTest {
     *     private Logger log;
     *     {@literal @}Rule
     *     public TestName name = new TestName();
     *     {@literal @}Test
     *     public void testMethod() throws Exception {
     *         log = TestSetup.setup(name);
     *       ...
     *     }
     * }</code></pre>
     */
    public static Logger setup(TestName name, String[][] props)
        throws Exception
    {
        // First set default system properties for a few important ones.
        setSystemProperties(new String[][] {
            {"log", "testlog"},
            {"log_level", "TRACE"},
            {"item_source", "test"},
            {"item_source_id_type", "aiid"}
        });

        // Then set "overrides"
        setSystemProperties(props);

        Logger log = LoggerFactory.getLogger(name.getClass());
        log.info("=====================================================================");
        log.info("Starting test " + name.getMethodName());
        App.init();
        return log;
    }

    /**
     * A convenience form of setup, for use when no default system properties need
     * to be overridden.
     */
    public static Logger setup(TestName name)
        throws Exception
    {
        return setup(name, null);
    }

    /**
     * Helper function - this sets a list of system properties.
     */
    public static void setSystemProperties(String[][] pairs) {
        if (pairs != null) {
            for (String[] pair : pairs) {
                System.setProperty(pair[0], pair[1]);
            }
        }
    }

    /**
     * Helper function to serialize XML for logging results.
     */
    public static String serializeXml(Document doc)    {
        try
        {
           DOMSource domSource = new DOMSource(doc);
           StringWriter writer = new StringWriter();
           StreamResult result = new StreamResult(writer);
           TransformerFactory tf = TransformerFactory.newInstance();
           Transformer transformer = tf.newTransformer();
           transformer.transform(domSource, result);
           writer.flush();
           return writer.toString();
        }
        catch(TransformerException ex)
        {
           ex.printStackTrace();
           return null;
        }
    }
}
