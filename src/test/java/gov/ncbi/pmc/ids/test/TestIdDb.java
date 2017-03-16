package gov.ncbi.pmc.ids.test;

import static gov.ncbi.pmc.ids.test.Helper.checkId;
import static gov.ncbi.pmc.ids.test.Helper.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;

import gov.ncbi.pmc.ids.IdDb;
import gov.ncbi.pmc.ids.IdType;
import gov.ncbi.pmc.ids.Identifier;


/**
 * These tests are parameterized, so that they all run twice; once for each:
 * - Using the static global `litIds`
 * - Using an IdDb that it reads from a JSON file src/test/resources/literature-id-db.json
 */
@RunWith(Parameterized.class)
public class TestIdDb
{
    private IdDb iddb;

    public TestIdDb(IdDb iddb) {
        this.iddb = iddb;
    }


    /**
     * Read a test IdDb from a JSON file
     */
    private static IdDb _fromJson()
        throws IOException
    {
        URL jsonUrl = TestIdDb.class.getClassLoader().getResource("literature-id-db.json");
        return IdDb.fromJson(jsonUrl);
    }



    @Parameters
    public static Collection<Object[]> data()
        throws IOException
    {
        Collection<Object[]> ret = new ArrayList<Object[]>();
        ret.add(new Object[] { IdDb.litIds });
        ret.add(new Object[] { _fromJson() });
        return ret;
    }


    @Rule
    public TestName name = new TestName();

    /**
     * Test getting types, and their names
     */
    @Test
    public void testTypesNames()
    {
        String testName = name.getMethodName() + " with " + iddb.getName();
        Logger log = setup(TestIdDb.class, testName);
        log.debug("iddb: " + iddb);
        boolean exceptionThrown;

        assertNotNull(iddb);
        //assertEquals("literature-ids", iddb.getName());
        assertEquals(5, iddb.size());

        IdType pmid = iddb.getType("pmid");
        IdType pmcid = iddb.getType("pmcid");
        IdType mid = iddb.getType("mid");
        IdType doi = iddb.getType("doi");
        IdType aiid = iddb.getType("aiid");

        List<IdType> types = iddb.getAllTypes();
        assertEquals(pmid, types.get(0));
        assertEquals(pmcid, types.get(1));
        assertEquals(mid, types.get(2));
        assertEquals(doi, types.get(3));
        assertEquals(aiid, types.get(4));

        assertEquals("pmid", pmid.getName());
        assertEquals("pmcid", pmcid.getName());
        assertEquals("mid", mid.getName());
        assertEquals("doi", doi.getName());
        assertEquals("aiid", aiid.getName());

        // Check that the getType lookup is case-insensitive:
        IdType pmid0 = iddb.getType("PmId");
        assertEquals(pmid, pmid0);

        // Check that a bad name returns null
        IdType bingo = iddb.lookupType("bingo");
        assertNull(bingo);

        exceptionThrown = false;
        try {
            @SuppressWarnings("unused")
            IdType fleegle = iddb.getType("fleegle");
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        // So does null
        assertNull(iddb.getType(null));

        log.info(testName + " done.");
    }

    /**
     * Test matching strings against patterns.
     */
    @Test
    public void testFindAndMatchType()
    {
        String testName = name.getMethodName() + " with " + iddb.getName();
        Logger log = setup(TestIdDb.class, testName);

        String id;
        boolean exceptionThrown;

        IdType pmid = iddb.getType("pmid");
        IdType pmcid = iddb.getType("pmcid");
        IdType mid = iddb.getType("mid");
        IdType doi = iddb.getType("doi");
        IdType aiid = iddb.getType("aiid");

        assertEquals(pmid, iddb.findType("123456"));
        assertEquals(pmcid, iddb.findType("pMc3455"));
        assertEquals(mid, iddb.findType("pdMc3455"));
        assertEquals(doi, iddb.findType("10.1093/cercor/bhs015"));
        assertNull(iddb.findType("ukn.8778.ci095-1"));

        assertEquals(pmid, iddb.findType((String) null, "123456"));
        assertEquals(pmcid, iddb.findType((String) null, "pMc3455"));
        assertEquals(mid, iddb.findType((String) null, "pdMc3455"));
        assertEquals(doi, iddb.findType((String) null, "10.1093/cercor/bhs015"));
        assertNull(iddb.findType((String) null, "ukn.8778.ci095-1"));

        assertEquals(pmid, iddb.findType("pmid", "123456"));
        assertEquals(aiid, iddb.findType("aiid", "123456"));
        assertEquals(mid, iddb.findType("mid", "PMC123456"));
        assertNull(iddb.findType("doi", "ukn.8778.ci095-1"));
        assertNull(iddb.findType("fleegle", "12345"));

        assertEquals(pmid, iddb.findType(pmid, "123456"));
        assertEquals(aiid, iddb.findType(aiid, "123456"));
        assertEquals(mid, iddb.findType(mid, "PMC123456"));
        assertNull(iddb.findType(doi, "ukn.8778.ci095-1"));

        assertEquals(pmid, iddb.findType("pmid:123456"));
        assertEquals(aiid, iddb.findType("aiid:123456"));
        assertEquals(mid, iddb.findType("mid:PMC123456"));
        assertNull(iddb.findType(doi, "doi:ukn.8778.ci095-1"));
        assertNull(iddb.findType(pmid, "aiid:12345"));


        // Check an ID string that matches multiple types
        id = "76389932";
        List<IdType> types = iddb.findAllTypes(id);
        assertEquals(3, types.size());
        assert(types.contains(pmid));
        assert(types.contains(pmcid));
        assertFalse(types.contains(mid));
        assertFalse(types.contains(doi));
        assert(types.contains(aiid));

        id = "76389932";
        assert(iddb.valid("pmid", id));
        assert(iddb.valid("pmcid", id));
        assertFalse(iddb.valid("mid", id));
        assertFalse(iddb.valid("doi", id));
        assert(iddb.valid("aiid", id));

        log.info(testName + " done.");
    }

    /**
     * Test constructing Identifiers in a variety of ways.
     */
    @Test
    public void testMakingIds()
    {
        String testName = name.getMethodName() + " with " + iddb.getName();
        Logger log = setup(TestIdDb.class, testName);

        IdType pmid = iddb.getType("pmid");
        IdType pmcid = iddb.getType("pmcid");
        IdType mid = iddb.getType("mid");
        IdType doi = iddb.getType("doi");
        IdType aiid = iddb.getType("aiid");

        Identifier id;
        boolean exceptionThrown;

        // Make Identifiers from value strings alone

        id = iddb.id("123456");
        checkId("1.1", pmid, "123456", "pmid:123456", false, id);

        id = iddb.id("44567.8");
        checkId("1.2", pmid, "44567.8", "pmid:44567.8", true, id);

        id = iddb.id("pmC899476");
        checkId("1.3", pmcid, "PMC899476", "pmcid:PMC899476", false, id);

        id = iddb.id("PmC44567.8");
        checkId("1.4", pmcid, "PMC44567.8", "pmcid:PMC44567.8", true, id);

        id = iddb.id("Squabble3");
        checkId("1.5", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id("10.1016/j.fsi.2017.03.003");
        checkId("1.6", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid id string
        assertNull("1.7", iddb.id("7U.8*5-uuj"));


        // Same tests but using makeId(null, value)
        id = iddb.id((String) null, "123456");
        checkId("2.1", pmid, "123456", "pmid:123456", false, id);

        id = iddb.id((String) null, "44567.8");
        checkId("2.2", pmid, "44567.8", "pmid:44567.8", true, id);

        id = iddb.id((String) null, "pmC899476");
        checkId("2.3", pmcid, "PMC899476", "pmcid:PMC899476", false, id);

        id = iddb.id((String) null, "PmC44567.8");
        checkId("2.4", pmcid, "PMC44567.8", "pmcid:PMC44567.8", true, id);

        id = iddb.id((String) null, "Squabble3");
        checkId("2.5", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id((String) null, "10.1016/j.fsi.2017.03.003");
        checkId("2.6", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid id string
        id = iddb.id((String) null, "7U.8*5-uuj");
        assertNull("2.7", id);


        // Specify the type by name (case insensitive)

        id = iddb.id("pmid", "487365");
        checkId("3.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id("AIID", "487365");
        checkId("3.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id("PMCid", "84786");
        checkId("3.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id("pmcID", "84786.1");
        checkId("3.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("pmcId", "PmC84786");
        assertTrue(iddb.valid("PmC84786"));
        checkId("3.5", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id("pmcid", "PMc84786.1");
        assertTrue(iddb.valid("PMc84786.1"));
        checkId("3.6", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("Mid", "Squabble3");
        assertTrue(iddb.valid("Squabble3"));
        checkId("3.7", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id("DOI", "10.1016/j.fsi.2017.03.003");
        checkId("3.8", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // bad type name
        id = iddb.id("foo", "77876");
        assertNull("3.9", id);

        // invalid string
        id = iddb.id("pMcid", "PPMC77684");
        assertNull("3.10", id);

        // Specify the type with IdType objects

        id = iddb.id(pmid, "487365");
        checkId("4.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id(aiid, "487365");
        checkId("4.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id(pmcid, "84786");
        checkId("4.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "84786.1");
        checkId("4.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(pmcid, "PMc84786");
        checkId("4.5", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "PmC84786.1");
        checkId("4.6", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id(mid, "Squabble3");
        checkId("4.7", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id(doi, "10.1016/j.fsi.2017.03.003");
        checkId("4.8", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid string
        id = iddb.id(pmcid, "PPMC77684");
        assertNull("4.9", id);


        // Specify the type with prefixes (case insensitive)

        id = iddb.id("pmid:487365");
        checkId("5.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id("aIId:487365");
        checkId("5.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id("PMCid:84786");
        checkId("5.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id("pmcId:84786.1");
        checkId("5.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("pmcid:PMc84786");
        checkId("5.5", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id("PMCID:PmC84786.1");
        checkId("5.6", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("Mid:Squabble3");
        checkId("5.7", mid, "SQUABBLE3", "mid:SQUABBLE3", true, id);

        id = iddb.id("doi:10.1016/j.fsi.2017.03.003");
        checkId("5.8", doi, "10.1016/j.fsi.2017.03.003",
                "doi:10.1016/j.fsi.2017.03.003", false, id);

        // invalid string
        id = iddb.id("pmcid:PPMC77684");
        assertNull("5.9", id);

        // Some combinations of type and prefixes

        id = iddb.id("pmid", "pmid:487365");
        checkId("6.1", pmid, "487365", "pmid:487365", false, id);

        id = iddb.id(aiid, "aIId:487365");
        checkId("6.2", aiid, "487365", "aiid:487365", true, id);

        id = iddb.id("PmCiD", "PMCid:84786");
        checkId("6.3", pmcid, "PMC84786", "pmcid:PMC84786", false, id);

        id = iddb.id(pmcid, "pmcId:84786.1");
        checkId("6.4", pmcid, "PMC84786.1", "pmcid:PMC84786.1", true, id);

        id = iddb.id("pmid", "pmcid:PMc84786");
        assertNull("6.5", id);

        id = iddb.id(mid, "pmcid:pmc84786.1");
        assertNull("6.6", id);

        log.info(testName + " done.");
    }
}
