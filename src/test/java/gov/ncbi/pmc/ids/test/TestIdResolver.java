package gov.ncbi.pmc.ids.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import gov.ncbi.pmc.cite.BadParamException;
import gov.ncbi.pmc.ids.IdGlob;
import gov.ncbi.pmc.ids.IdResolver;
import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;
import gov.ncbi.pmc.ids.RequestIdList;

import org.slf4j.Logger;

public class TestIdResolver
{
    private Logger log;

    @Rule
    public TestName name = new TestName();

    /**
     * Set up the testing environment
     */
    @Before
    public void setUp() {
        System.setProperty("log", "testlog");
        System.setProperty("log_level", "DEBUG");
    }

    /**
     * Test the Identifier class
     */
    @Ignore
    @Test
    public void testIdentifier() {
        Identifier id0 = null;
        Identifier id1 = null;
        boolean exceptionThrown;

        try {
            id0 = new Identifier("12345", "pmid");
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        assertEquals("pmid", id0.getType());
        assertEquals("12345", id0.getValue());
        assertEquals("pmid:12345", id0.toString());

        // Test bad type
        exceptionThrown = false;
        try {
            id0 = new Identifier("1234", "foo");
        }
        catch (BadParamException e) {
            exceptionThrown = true;
        }
        assert(exceptionThrown);

        // Test a bad pattern
        exceptionThrown = false;
        try {
            id0 = new Identifier("1234x", "pmcid");
        }
        catch (BadParamException e) {
            exceptionThrown = true;
        }
        assert(exceptionThrown);

        // Test canonicalization
        try {
            id0 = new Identifier("pmc12345", "pmcid");
            id1 = new Identifier("2345", "pmcid");
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        assertEquals("pmcid:PMC12345", id0.toString());
        assertEquals("pmcid:PMC2345", id1.toString());
    }

    /**
     * Test the IdGlob class
     */
    @Test
    public void testIdGlob() {
        IdGlob idg = null;

        idg = new IdGlob();
        assertFalse(idg.isVersioned());

        // Add some Identifiers
        try {
            idg.addId(new Identifier("1234", "pmid"));
            idg.addId(new Identifier("2345", "pmcid"));
            idg.addId(new Identifier("10.12/23/45", "doi"));
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        assert(idg.hasType("pmid"));
        assert(idg.hasType("pmcid"));
        assert(idg.hasType("doi"));
        assertFalse(idg.hasType("mid"));
        assertEquals("pmid:1234", idg.getIdByType("pmid").toString());
        assertEquals("pmcid:PMC2345", idg.getIdByType("pmcid").toString());
        assertEquals("doi:10.12/23/45", idg.getIdByType("doi").toString());

        // Let's add a couple of version kids
        IdGlob kid0, kid1;
        kid0 = new IdGlob();
        try {
            kid0.addId(new Identifier("NIHMS444", "mid"));
            kid0.addId(new Identifier("2345.1", "pmcid"));
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        idg.addVersion(kid0);

        kid1 = new IdGlob();
        try {
            kid1.addId(new Identifier("NIHMS445", "mid"));
            kid1.addId(new Identifier("2345.2", "pmcid"));
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        idg.addVersion(kid1);

        assertFalse(idg.isVersioned());
        assert(kid0.isVersioned());
        assert(kid1.isVersioned());

        // Lookie, we can get the pmid and doi of the kids:
        assertEquals("pmid:1234", kid0.getIdByType("pmid").toString());
        assertEquals("doi:10.12/23/45", kid0.getIdByType("doi").toString());
        assertEquals("pmid:1234", kid1.getIdByType("pmid").toString());
        assertEquals("doi:10.12/23/45", kid1.getIdByType("doi").toString());
    }

    /**
     * Test the RequestId class
     */
    @Test
    public void testRequestId() {
        RequestId rid = null;
        String origType = "pmcid";
        String origString = "1234";
        Identifier origId = null;
        boolean exceptionThrown;

        try {
            origId = new Identifier(origString, origType);
            rid = new RequestId(origString, origId);
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        assertEquals(origType, rid.getType());
        assertEquals(origString, rid.getRequestedValue());
        assertEquals("pmcid:PMC1234", rid.getCanonical().toString());
        assertNotNull(rid.getIdGlob());
        assert(rid.getData() == null);
        rid.setData(false);
        assertFalse((Boolean) rid.getData());




        IdGlob idg = new IdGlob();
        try {
            idg.addId(new Identifier("1234", "pmid"));
            idg.addId(origId);
            idg.addId(new Identifier("10.12/23/45", "doi"));
            rid.setIdGlob(idg);
        }
        catch (Exception e) {
            fail("Got Exception: " + e.getMessage());
        }
        assertNotNull(rid.getIdGlob());

        // Verify that trying to set an IdGlob that doesn't have a matching id causes
        // an exception
        exceptionThrown = false;
        RequestId rid1 = new RequestId(origString, origId);
        IdGlob idg1 = new IdGlob();
        try {
            idg1.addId(new Identifier("1234", "pmid"));
            rid1.setIdGlob(idg1);
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assert(exceptionThrown);
    }

    /**
     * Test the RequestIdList.  Right now, there is only support for lists of IDs that
     * all have the same type.
     */
    @Test
    public void testRequestIdList() {
        RequestIdList idList = new RequestIdList();
        try {
            idList.add(buildRequestId("pmcid", "0000"));
            idList.add(buildRequestId("pmcid", "0111"));
            idList.add(buildRequestId("pmcid", "0222"));
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }

        assertEquals(3, idList.size());
        assertEquals("pmcid:PMC0000", idList.get(0).getCanonical().toString());
        assertEquals("pmcid:PMC0111", idList.get(1).getCanonical().toString());
        assertEquals("pmcid:PMC0222", idList.get(2).getCanonical().toString());

        try {
            int i = idList.lookup(new Identifier("pmc0111", "pmcid"));
            assertEquals(1, i);
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }

        // Add some globs to the ids
        try {
            RequestId rid0 = idList.get(0);
            rid0.setIdGlob(buildIdGlob(
                new String[] {"pmcid", "PMC0000", "pmid", "1000", "doi", "10.2000/0"}
            ));
            RequestId rid1 = idList.get(1);
            rid1.setIdGlob(buildIdGlob(
                new String[] {"pmcid", "PMC0111", "pmid", "1111", "doi", "10.2111/0"}
            ));
            RequestId rid2 = idList.get(2);
            rid2.setIdGlob(buildIdGlob(
                new String[] {"pmcid", "PMC0222", "pmid", "1222"}
            ));
        }
        catch (BadParamException e) {
            fail("Got BadParamException: " + e.getMessage());
        }

        assertEquals(3, idList.numHasType("pmcid"));
        assertEquals(2, idList.numHasType("doi"));
    }

    // Helper functions
    public static RequestId buildRequestId(String origType, String origString)
        throws BadParamException
    {
        Identifier origId = new Identifier(origString, origType);
        return new RequestId(origString, origId);
    }

    public static IdGlob buildIdGlob(String[] tv)
        throws BadParamException
    {
        IdGlob idg = new IdGlob();
        for (int i = 0; i < tv.length; i += 2) {
            idg.addId(new Identifier(tv[i+1], tv[i]));
        }
        return idg;
    }

    /**
     * Finally, test the master IdResolver class. This is really in integration test, not a unit
     * test, since it uses the real PMC ID Converter API.
     * You can test against the internal service by setting the `id_converter_url` system
     * property.
     */
    @Test
    public void testIdResolver() {
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
        assertEquals("pmcid:PMC3362639", rid0.getCanonical().toString());

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
        assertEquals("pmcid:PMC3159421", rid1.getCanonical().toString());
        idg1 = rid1.getIdGlob();
        assertNotNull(idg1);
        assertFalse(idg1.isVersioned());
        assertEquals("aiid:3159421", idg1.getIdByType("aiid").toString());
        assertEquals("doi:10.4242/BalisageVol7.Maloney01", idg1.getIdByType("doi").toString());
        assertEquals("pmid:21866248", idg1.getIdByType("pmid").toString());
    }
}
