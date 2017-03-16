package gov.ncbi.pmc.ids.test;

import static gov.ncbi.pmc.ids.test.Helper.aiid;
import static gov.ncbi.pmc.ids.test.Helper.checkId;
import static gov.ncbi.pmc.ids.test.Helper.doi;
import static gov.ncbi.pmc.ids.test.Helper.litIds;
import static gov.ncbi.pmc.ids.test.Helper.mid;
import static gov.ncbi.pmc.ids.test.Helper.pmcid;
import static gov.ncbi.pmc.ids.test.Helper.pmid;
import static gov.ncbi.pmc.ids.test.Helper.setup;
import static org.junit.Assert.*;
import static gov.ncbi.pmc.ids.RequestId.State.*;
import static gov.ncbi.pmc.ids.RequestId.B.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import gov.ncbi.pmc.ids.IdNonVersionSet;
import gov.ncbi.pmc.ids.IdSet;
import gov.ncbi.pmc.ids.IdType;
import gov.ncbi.pmc.ids.RequestId;
import gov.ncbi.pmc.ids.RequestId.B;
import gov.ncbi.pmc.ids.RequestId.State;

public class TestRequestId
{
    @Rule
    public TestName name = new TestName();

    private void _checkState(Logger log, State expState, RequestId rid)
    {
        assertEquals(expState, rid.getState());

        switch (expState) {
        case NOT_WELL_FORMED:
            assertFalse(rid.isWellFormed());
            assertTrue(rid.isResolved());
            assertEquals(rid.isGood(), FALSE);
            assertNull(rid.getMainId());
            assertNull(rid.getMainType());
            assertNull(rid.getMainValue());
            assertNull(rid.getMainCurie());
            assertNull(rid.getIdSet());
            break;

        case UNKNOWN:
            assertTrue(rid.isWellFormed());
            assertFalse(rid.isResolved());
            assertEquals(rid.isGood(), MAYBE);
            assertNotNull(rid.getMainId());
            break;

        case INVALID:
            assertTrue(rid.isWellFormed());
            assertTrue(rid.isResolved());
            assertEquals(rid.isGood(), FALSE);
            assertNotNull(rid.getMainId());
            break;

        case GOOD:
            assertTrue(rid.isWellFormed());
            assertTrue(rid.isResolved());
            assertEquals(rid.isGood(), TRUE);
            assertNotNull(rid.getMainId());
            break;
        }
    }

    /**
     * Test not-well-formed
     */
    @Test
    public void testNotWellFormed() {
        String testName = name.getMethodName();
        Logger log = setup(TestRequestId.class, testName);

        RequestId rid;

        rid = new RequestId(litIds, "shwartz:nothing");
        assertEquals(rid.getState(), NOT_WELL_FORMED);
        assertFalse(rid.isWellFormed());
        assertTrue(rid.isResolved());
        assertEquals(rid.isGood(), B.FALSE);

        log.info(testName + " done.");
    }

    @Test
    public void testUnknown() {
        String testName = name.getMethodName();
        Logger log = setup(TestRequestId.class, testName);

        RequestId rid = new RequestId(litIds, "pMC1234");
        assertEquals(rid.getState(), UNKNOWN);
        log.debug("RequestId: " + rid.toString());
        assertEquals(
            "{ requested: null:pMC1234, " +
                "parsed: pmcid:PMC1234 }",
            rid.toString());
        log.debug("  dumped: " + rid.dump());
        _checkState(log, UNKNOWN, rid);

        assertSame(litIds, rid.getIdDb());
        assertNull(rid.getRequestedType());
        assertEquals("pMC1234", rid.getRequestedValue());
        checkId(null, pmcid, "PMC1234", "pmcid:PMC1234", false,
            rid.getMainId());
        assertEquals(pmcid, rid.getMainType());
        assertEquals("PMC1234", rid.getMainValue());
        assertEquals("pmcid:PMC1234", rid.getMainCurie());
        assertTrue(rid.hasType(pmcid));
        assertFalse(rid.hasType(mid));

        checkId(null, pmcid, "PMC1234", "pmcid:PMC1234", false,
            rid.getId(pmcid));
        assertNull(rid.getId(mid));

        List<IdType> types = Arrays.asList(mid, pmid, pmcid);
        checkId(null, pmcid, "PMC1234", "pmcid:PMC1234", false,
                rid.getId(types));

        assertFalse(rid.isVersioned());
        assertNull(rid.getIdSet());

        log.info(testName + " done.");
    }

    @Test
    public void testInvalid() {
        String testName = name.getMethodName();
        Logger log = setup(TestRequestId.class, testName);
        boolean exceptionThrown;

        RequestId rid0 = new RequestId(litIds, "pMC1234");
        rid0.resolve(null);
        _checkState(log, INVALID, rid0);

        exceptionThrown = false;
        try {
            rid0.resolve(null);
        }
        catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        RequestId rid1 = new RequestId(litIds, "pMC1234");
        IdSet rset = (new IdNonVersionSet(litIds))
            .add(pmid.id("123456"),
                 pmcid.id("654321"),
                 doi.id("10.13/23434.56"));

        exceptionThrown = false;
        try {
            rid1.resolve(rset);
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        log.info(testName + " done.");
    }

    @Test
    public void testGood() {
        String testName = name.getMethodName();
        Logger log = setup(TestRequestId.class, testName);

        RequestId rid = new RequestId(litIds, "pMC1234");
        IdSet rset = (new IdNonVersionSet(litIds))
                .add(pmid.id("123456"),
                     pmcid.id("1234"),
                     doi.id("10.13/23434.56"));
        assertTrue(rset.sameId(rid.getMainId()));
        rid.resolve(rset);
        _checkState(log, GOOD, rid);

        log.info(testName + " done.");
    }
}
