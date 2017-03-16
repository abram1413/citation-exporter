package gov.ncbi.pmc.ids.test;

import static gov.ncbi.pmc.ids.test.Helper.litIds;
import static gov.ncbi.pmc.ids.test.Helper.pmid;
import static gov.ncbi.pmc.ids.test.Helper.setup;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ncbi.pmc.ids.IdResolver;


/**
 * This uses a mocked Jackson ObjectMapper for testing the calls to the
 * external ID resolver service. The responses are given in JSON files in
 * src/test/resources/id-resolver-mock-responses/.
 */
public class TestIdResolver
{
    @Rule
    public TestName name = new TestName();

    /// A real Jackson ObjectMapper used to read the mocked responses from JSON files
    ObjectMapper realMapper = new ObjectMapper();

    /// This function reads a mocked response from a JSON file
    JsonNode mockResolverResponse(String name)
        throws IOException
    {
        URL url = TestIdResolver.class.getClassLoader()
            .getResource("id-resolver-mock-responses/" + name + ".json");
        return realMapper.readTree(url);
    }

    /**
     * A custom ArgumentMatcher used with Mockito that checks the argument to
     * the mocked ObjectMapper's readTree() method (which is a URL), looking
     * for a part of the query string.
     */
    class IdArgMatcher implements ArgumentMatcher<URL> {
        private String _expectedStr;
        public IdArgMatcher(String expectedStr) {
            _expectedStr = expectedStr;
        }
        @Override
        public boolean matches(URL url) {
            if (url == null || url.getQuery() == null) return false;
            return url.getQuery().matches("^.*" + _expectedStr + ".*$");
        }
        @Override
        public String toString() {
            return "[URL expected to contain " + _expectedStr + "]";
        }
    }

    /**
     * This cross-references the pattern that we look for in the query string
     * to the name of the JSON file containing the mocked response.
     */
    String[][] resolverNodes = new String[][] {
        new String[] { "idtype=pmid&ids=26829486,22368089", "two-good-pmids" },
        new String[] { "idtype=pmid&ids=26829486,7777", "one-good-one-bad-pmid" },
    };

    /**
     * Test the IdResolver class constructors and data access methods.
     */
    @Test
    public void testConstructor()
        throws Exception
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdResolver.class, testName);

        IdResolver resolver = new IdResolver(litIds, pmid);
        assertTrue(litIds == resolver.getIdDb());
        assertEquals(pmid, resolver.getWantedType());

        log.debug(resolver.getConfig());

        log.info(testName + " done.");
    }

   /**
    * For reference, here's the call tree of resolveIds():
    * - parseRequestIds(String, String)
    *     - new RequestId(IdDb, String, String)
    * - groupsToResolve(List<RequestId>)
    * - resolverUrl(IdType, List<RequestId>
    * - recordFromJson(ObjectNode, IdNonVersionSet)
    * - findAndBind(IdType, List<RequestId>, IdSet)

    @Test
    public void testParseRequestIds()
        throws Exception
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdResolver.class, testName);

        IdResolver resolver = new IdResolver(litIds, pmid);
        List<RequestId> rids;

        rids = resolver.parseRequestIds("pMid", "12345,67890");
        assertEquals(2, rids.size());
        RequestId rid0 = rids.get(0);
        assertEquals(State.UNKNOWN, rid0.getState());
        assertTrue(rid0.isWellFormed());
        assertFalse(rid0.isResolved());
        assertEquals(B.MAYBE, rid0.isGood());
        assertEquals("pMid", rid0.getRequestedType());
        assertEquals("12345", rid0.getRequestedValue());
        assertEquals(pmid.id("12345"), rid0.getMainId());

        // mixed types
        rids = resolver.parseRequestIds(null, "PMC6788,845763,NIHMS99878,PMC778.4");
        assertEquals(4, rids.size());
        assertEquals(pmcid, rids.get(0).getMainType());
        assertEquals(pmid, rids.get(1).getMainType());
        assertEquals(mid, rids.get(2).getMainType());
        assertEquals(pmcid, rids.get(3).getMainType());

        // some non-well-formed
        rids = resolver.parseRequestIds("pmcid", "PMC6788,845763,NIHMS99878,PMC778.4");
        assertEquals(4, rids.size());
        assertEquals(pmcid.id("PMC6788"), rids.get(0).getMainId());
        log.debug("should be non-well-formed: " + rids.get(1));
        assertEquals(pmcid.id("PMC845763"), rids.get(1).getMainId());
        assertEquals(State.NOT_WELL_FORMED, rids.get(2).getState());
        assertEquals(pmcid.id("PMC778.4"), rids.get(3).getMainId());

        log.info(testName + " done.");
    }


    @Test
    public void testResolverUrl()
        throws Exception
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdResolver.class, testName);

        IdResolver resolver = new IdResolver(litIds, pmid);
        String baseUrl =
            "https://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/?" +
            "showaiid=yes&format=json&tool=ctxp&" +
            "email=pubmedcentral@ncbi.nlm.nih.gov&";

        IdType fromType;
        List<RequestId> rids;

        fromType = pmid;
        rids = Arrays.asList(
            new RequestId(litIds, "pmid", "34567"),
            new RequestId(litIds, "pmid", "77898")
        );
        log.debug("rids: " + rids);
        URL resUrl = resolver.resolverUrl(fromType, rids);
        log.debug("resolver URL: " + resUrl);
        assertEquals(baseUrl + "idtype=pmid&ids=34567,77898",
                resUrl.toString());

        // FIXME: Test some versioned ones

        log.info(testName + " done.");
    }

    @Test
    public void testGlobbify()
        throws Exception
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdResolver.class, testName);

        IdResolver resolver = new IdResolver(litIds, pmid);

        JsonNode record = mockResolverResponse("basic-one-kid");
        log.debug("About to globbify record: " + record);
        IdSet parent = resolver.recordFromJson((ObjectNode) record, null);
        log.debug("parent: " + parent.toString());

        // Note that the `aiid` at the top level is ignored
        assertTrue(parent.sameId(pmcid.id("PMC4734780")));
        assertTrue(parent.sameId(pmid.id("26829486")));
        assertTrue(parent.sameId(doi.id("10.1371/journal.pntd.0004413")));

        assertTrue(parent.sameWork(pmcid.id("4734780.1")));
        assertTrue(parent.sameWork(aiid.id("4734780")));

        log.info(testName + " done.");
    }


    /*

        // Create and initialize the mocked ObjectMapper. When its readTree() method
        // is called, it will return a response from one of
        // the JSON files in id-resolver-mock-responses/.
        ObjectMapper mockMapper = mock(ObjectMapper.class);

        for (String[] pair : resolverNodes) {
            String pattern = pair[0];
            String name = pair[1];
            when(mockMapper.readTree(argThat(new IdArgMatcher(pattern))))
                .thenReturn(mockResolverResponse(name));
        }

        // Create a new IdResolver that "wants" pmcids
        IdResolver resolver = new IdResolver(litIds, pmcid, mockMapper);
        assert(litIds == resolver.getIdDb());
        assertEquals(pmcid, resolver.getWantedType());

        List<RequestId> ridList = resolver.resolveIds("26829486,22368089");
        assertEquals(2, ridList.size());

        RequestId rid0 = ridList.get(0);
        assert(rid0.same(pmid.id("26829485")));
        assert(rid0.same(pmcid.id("PMC4734780")));
        assert(rid0.same("pmcid:PMC4734780"));
        assert(rid0.same("PMC4734780"));
        assert(rid0.same("pmcid:4734780"));
        assert(rid0.same("pmcid:4734780"));

        ridList =  resolver.resolveIds("26829486,7777");

/*

        RequestId rid0, rid1;
        IdGlob idg0, idg1;
        IdResolver resolver;

        resolver = new IdResolver();
        RequestIdList idList = null;
        try {
            idList = resolver.resolveIds("PMC3362639,Pmc3159421");
        }
        catch(Exception e) {
            fail("Got an Exception: " + e.getMessage());
        }
        assertEquals(2, idList.size());

        // Check the first ID
        rid0 = idList.get(0);
        assertEquals("pmcid", rid0.getType());
        assertEquals("PMC3362639", rid0.getRequestedValue());
        assertEquals("pmcid:PMC3362639", rid0.getId().toString());

        idg0 = rid0.getIdGlob();
        assertNotNull(idg0);
        assertFalse(idg0.isVersioned());
        assertEquals("aiid:3362639", idg0.getIdByType("aiid").toString());
        assertEquals("doi:10.1371/journal.pmed.1001226", idg0.getIdByType("doi").toString());
        assertNull(idg0.getIdByType("pmid"));

        // Check the second ID
        rid1 = idList.get(1);
        assertEquals("pmcid", rid1.getType());
        assertEquals("Pmc3159421", rid1.getRequestedValue());
        assertEquals("pmcid:PMC3159421", rid1.getId().toString());
        idg1 = rid1.getIdGlob();
        assertNotNull(idg1);
        assertFalse(idg1.isVersioned());
        assertEquals("aiid:3159421", idg1.getIdByType("aiid").toString());
        assertEquals("doi:10.4242/BalisageVol7.Maloney01", idg1.getIdByType("doi").toString());
        assertEquals("pmid:21866248", idg1.getIdByType("pmid").toString());

        log.info(testName + " done.");
    }
      */
}
