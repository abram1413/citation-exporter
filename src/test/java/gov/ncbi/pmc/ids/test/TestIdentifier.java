package gov.ncbi.pmc.ids.test;

import static gov.ncbi.pmc.ids.test.Helper.aiid;
import static gov.ncbi.pmc.ids.test.Helper.checkId;
import static gov.ncbi.pmc.ids.test.Helper.litIds;
import static gov.ncbi.pmc.ids.test.Helper.mid;
import static gov.ncbi.pmc.ids.test.Helper.pmcid;
import static gov.ncbi.pmc.ids.test.Helper.pmid;
import static gov.ncbi.pmc.ids.test.Helper.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import gov.ncbi.pmc.ids.Identifier;

public class TestIdentifier
{
    @Rule
    public TestName name = new TestName();

    /**
     * Test the Identifier class.
     */
    @Test
    public void testIdentifier()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdentifier.class, testName);

        Identifier id0 = pmcid.id("PMC123456");
        checkId("id0", pmcid, "PMC123456", "pmcid:PMC123456", false, id0);

        assertSame(litIds, id0.getIdDb());
        assertEquals(pmcid, id0.getType());
        assertEquals(id0.getCurie(), id0.toString());

        Identifier id1 = litIds.id(pmcid, "pmC123456");
        checkId("id1", pmcid, "PMC123456", "pmcid:PMC123456", false, id1);
        assertEquals(id0, id1);

        Identifier id2 = litIds.id("pmcid:pMc123456");
        checkId("id2", pmcid, "PMC123456", "pmcid:PMC123456", false, id2);
        assertEquals(id0, id2);
    }

    /**
     * Test constructing and querying Identifier objects.
     */
    @Test
    public void testMakeFromIdType()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdentifier.class, testName);

        Identifier id;

        id = pmid.id("123456");
        checkId("check 1", pmid, "123456", "pmid:123456", false, id);
        assertSame(litIds, id.getIdDb());

        id = pmid.id("123456.7");
        checkId("check 2", pmid, "123456.7", "pmid:123456.7", true, id);

        id = pmcid.id("899476");
        checkId("check 3", pmcid, "PMC899476", "pmcid:PMC899476", false, id);

        id = pmcid.id("Pmc3333.4");
        checkId("check 4", pmcid, "PMC3333.4", "pmcid:PMC3333.4", true, id);

        Identifier id0 = pmcid.id("PMC3333.4");
        assertEquals("PMC3333.4 identifiers equal", id, id0);

        id = mid.id("NIHMS65432");
        checkId("check 5", mid, "NIHMS65432", "mid:NIHMS65432", true, id);

        id = aiid.id("899476");
        checkId("check 6", aiid, "899476", "aiid:899476", true, id);

        id = pmcid.id("ABD7887466");
        assertNull("check 7", id);

        log.info(testName + " done.");
    }

    /**
     * Test equals and hashCode
     */
    @Test
    public void testEquals()
    {
        Identifier x = pmcid.id("778476");
        assertTrue(x.equals(x));
        assertFalse(x.equals(null));

        Identifier y = pmcid.id("PMC778476");
        assertTrue(y.equals(y));
        assertFalse(y.equals(null));

        assertNotSame(x, y);
        assertTrue(x.equals(y));
        assertTrue(y.equals(x));
        assertEquals(x.hashCode(), y.hashCode());

        assertFalse(x.equals("PMC778476"));

        Stream.of(pmid.id("778476"), pmcid.id("778576")).forEach(id -> {
            assertFalse(x.equals(id));
            assertFalse(id.equals(x));
            assertFalse(y.equals(id));
            assertFalse(id.equals(y));
        });
    }
}
