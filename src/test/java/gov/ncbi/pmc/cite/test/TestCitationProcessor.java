package gov.ncbi.pmc.cite.test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import de.undercouch.citeproc.output.Bibliography;
import gov.ncbi.pmc.cite.App;
import gov.ncbi.pmc.cite.CitationProcessor;
import gov.ncbi.pmc.cite.CiteprocPool;
import gov.ncbi.pmc.cite.NotFoundException;
import gov.ncbi.pmc.ids.IdResolver;
import gov.ncbi.pmc.ids.RequestIdList;

/**
 * Unit test for CitationProcessor.
 */
public class TestCitationProcessor
{
    protected App app;
    @SuppressWarnings("unused")
    private Logger log;
    CiteprocPool cpp;
    IdResolver idResolver;

    @Rule
    public TestName name = new TestName();

    @Before
    public void setup()  throws Exception
    {}

    /**
     * Test some exception conditions
     */
    @Test
    public void testCitationProcessor() throws Exception
    {
        assertEquals("testCitationProcessor", name.getMethodName());
        log = Utils.setup(name);
        cpp = App.getCiteprocPool();
        idResolver = App.getIdResolver();

        boolean thrown = false;
        try {
            @SuppressWarnings("unused")
            CitationProcessor cp = cpp.getCiteproc("fleegle");
        }
        catch (NotFoundException e) {
            thrown = true;
        }
        assertTrue("Expected NotFoundException", thrown);

        CitationProcessor cp = cpp.getCiteproc("zdravniski-vestnik");
        assertEquals("zdravniski-vestnik", cp.getStyle());

        // Try a known-bad list of IDs, make sure that when we do
        // prefetchItems, we get a NotFoundException
        // Use type `aiid`, so that the resolver won't go out to the web
        // service to try to resolve these.
        RequestIdList idList = idResolver.resolveIds("4321332,4020095", "aiid");
        thrown = false;
        try {
            cp.prefetchItems(idList);
        }
        catch (NotFoundException e) {
            thrown = true;
        }
        assertTrue("Expected to get a NotFoundException", thrown);
    }

    /**
     * Test generating a bibliography from citeproc-json.
     * @throws Exception
     */
    @Test
    public void testBiblFromJson() throws Exception
    {
        assertEquals("testBiblFromJson", name.getMethodName());
        log = Utils.setup(name);
        cpp = App.getCiteprocPool();
        idResolver = App.getIdResolver();

        CitationProcessor cp = cpp.getCiteproc("zdravniski-vestnik");
        RequestIdList idList = idResolver.resolveIds("21", "aiid");
        Bibliography bibl = cp.makeBibliography(idList);
        String result = bibl.makeString();
        assertThat(result, containsString("Malone K. Chapters on " +
            "Chaucer. Baltimore: Johns Hopkins Press; 1951."));
    }

    /**
     * Test generating a bibliography from single-record PubOne; the
     * kind we see from PMC's stcache.
     * @throws Exception
     */
    @Test
    public void testBiblFromPmcPubOne() throws Exception
    {
        assertEquals("testBiblFromPmcPubOne", name.getMethodName());
        log = Utils.setup(name);
        cpp = App.getCiteprocPool();
        idResolver = App.getIdResolver();

        CitationProcessor cp = cpp.getCiteproc("zdravniski-vestnik");
        RequestIdList idList = idResolver.resolveIds("30", "aiid");

        Bibliography bibl = cp.makeBibliography(idList);
        String result = bibl.makeString();
        assertThat(result, containsString("Moon S, Bermudez J, ’t Hoen " +
            "E. Innovation and Access"));
        assertThat(result, containsString("Broken " +
            "Pharmaceutical R&#38;D System"));
    }

    /**
     * Test generating a bibliography from a PubOne sample record from the
     * PubMed backend (see NS-454).
     */
    @Test
    public void testBiblFromPubMedPubOne() throws Exception
    {
        assertEquals("testBiblFromPubMedPubOne", name.getMethodName());
        log = Utils.setup(name, new String[][] {
            {"item_source_id_type", "pmid"}
        });
        cpp = App.getCiteprocPool();
        idResolver = App.getIdResolver();

        CitationProcessor cp = cpp.getCiteproc("zdravniski-vestnik");
        log.debug("Resolving pmid:25651787");
        RequestIdList idList = idResolver.resolveIds("25651787", "pmid", "pmid");
        log.debug("Resolved: " + idList.toString());

        Bibliography bibl = cp.makeBibliography(idList);
        String result = bibl.makeString();
        assertThat(result, containsString("Torre LA, Bray F, Siegel RL"));
    }

    /**
     * Test generating a bibliography from PMC NXML.
     */
    @Test
    public void testBiblFromNxml() throws Exception
    {
        assertEquals("testBiblFromNxml", name.getMethodName());
        log = Utils.setup(name);
        cpp = App.getCiteprocPool();
        idResolver = App.getIdResolver();

        CiteprocPool cpp = App.getCiteprocPool();
        CitationProcessor cp = cpp.getCiteproc("zdravniski-vestnik");
        IdResolver idResolver = App.getIdResolver();
        RequestIdList idList = idResolver.resolveIds("3352855", "aiid");

        Bibliography bibl = cp.makeBibliography(idList);
        String result = bibl.makeString();
        assertThat(result, containsString("Moon S, Bermudez J, ’t Hoen " +
                "E. Innovation and Access"));
    }
}
