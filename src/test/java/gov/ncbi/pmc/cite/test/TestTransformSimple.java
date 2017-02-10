package gov.ncbi.pmc.cite.test;

import static gov.ncbi.pmc.cite.test.Utils.serializeXml;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import gov.ncbi.pmc.cite.App;
import gov.ncbi.pmc.cite.ItemSource;
import gov.ncbi.pmc.cite.TransformEngine;
import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

/**
 * This does some simple, manual unit tests of the code that drives the
 * XSLT transforms.
 *
 * Data-driven schematron and regular-expression matching tests are done in
 * TransformTest.java.
 */
public class TestTransformSimple {
    protected App app;
    private Logger log;

    @Rule
    public TestName name = new TestName();

    /**
     * Test simply retrieving an NXML.
     */
    @Test
    public void testGetNxml() throws Exception
    {
        log = Utils.setup(name);
        ItemSource itemSource = App.getItemSource();

        Document nxml_31 = null;
        RequestId requestId = new RequestId("31", new Identifier("31", "aiid"));
        nxml_31 = itemSource.retrieveItemNxml(requestId);
        assertEquals(nxml_31.getDocumentElement().getTagName(), "article");
    }

    /**
     * Test the identity transform.
     */
    @Test
    public void testIdentityTransform() throws Exception
    {
        log = Utils.setup(name);
        Document in, out;
        TransformEngine engine = App.getTransformEngine();

        in = xmlFromString("<foo/>");
        out = (Document) engine.doTransform(in, "identity");
        log.trace("Output XML:\n" + serializeXml(out));
        assertEquals("foo", out.getDocumentElement().getTagName());

        in = xmlFromString(
            "<splits>\n" +
            "  <fleegle plays='guitar'/>\n" +
            "  <bingo plays='drums'/>\n" +
            "</splits>\n");
        out = (Document) engine.doTransform(in, "identity");
        log.trace("Output XML:\n" + serializeXml(out));
        Element outRoot = out.getDocumentElement();
        assertEquals("splits", outRoot.getTagName());
        // child nodes including whitespace text nodes
        assertEquals(5, outRoot.getChildNodes().getLength());
    }

    /**
     * Test the string-value transform.
     */
    @Test
    public void testStringValueTransform() throws Exception
    {
        log = Utils.setup(name);
        Document in;
        String out;
        TransformEngine engine = App.getTransformEngine();

        in = xmlFromString("<foo>bar</foo>");
        out = (String) engine.doTransform(in, "string-value");
        log.trace("Output String:\n" + out);
        assertThat(out, containsString("bar"));

        in = xmlFromString(
            "<splits>\n" +
            "  <split><name>fleegle</name>: <plays>guitar</plays></split>\n" +
            "  <split><name>bingo</name>: <plays>drums</plays></split>\n" +
            "</splits>\n");
        out = (String) engine.doTransform(in, "string-value");
        assertThat(out, containsString("fleegle: guitar"));
        log.trace("Output String:\n" + out);
    }

    /**
     * Test transform with parameters.
     */
    @Test
    public void testParams() throws Exception
    {
        log = Utils.setup(name);
        Document in;
        Map<String, String> params = new HashMap<String, String>();
        String out;
        String expect;
        TransformEngine engine = App.getTransformEngine();

        in = xmlFromString("<foo/>");

        // With no params, we should see the default value
        out = (String) engine.doTransform(in, "test-params");
        log.trace("Output String:\n" + out);
        expect = "default param1 value";
        assertThat(out, containsString(expect));

        // Empty params map should work the same way
        out = (String) engine.doTransform(in, "test-params", params);
        log.trace("Output String:\n" + out);
        assertThat(out, containsString(expect));

        // Now, pass in a real param
        expect = "Zombo and Wumbus rock!";
        params.put("param1", expect);
        out = (String) engine.doTransform(in, "test-params", params);
        log.trace("Output String:\n" + out);
        assertThat(out, containsString(expect));
    }

    /**
     * Test that transforms with our custom URI resolver work.
     */
    @Test
    public void testTransformWithResolver() throws Exception
    {
        log = Utils.setup(name);
        Document in, out;
        TransformEngine engine = App.getTransformEngine();

        in = xmlFromString("<foo/>");
        out = (Document) engine.doTransform(in, "test-resolver");
        log.trace("Output XML:\n" + serializeXml(out));
        assertEquals("foo", out.getDocumentElement().getTagName());
    }

    /**
     * Helper function to create a JAXP Document object from a string of xml.
     */
    public static Document xmlFromString(String s) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(s));
        return db.parse(is);
    }
}

