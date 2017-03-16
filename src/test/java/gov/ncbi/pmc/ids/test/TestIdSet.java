package gov.ncbi.pmc.ids.test;

import static gov.ncbi.pmc.ids.IdSet.IdScope.IDENTITY;
import static gov.ncbi.pmc.ids.IdSet.IdScope.INSTANCE;
import static gov.ncbi.pmc.ids.IdSet.IdScope.WORK;
import static gov.ncbi.pmc.ids.test.Helper.aiid;
import static gov.ncbi.pmc.ids.test.Helper.doi;
import static gov.ncbi.pmc.ids.test.Helper.litIds;
import static gov.ncbi.pmc.ids.test.Helper.mid;
import static gov.ncbi.pmc.ids.test.Helper.pmcid;
import static gov.ncbi.pmc.ids.test.Helper.pmid;
import static gov.ncbi.pmc.ids.test.Helper.privateMethod;
import static gov.ncbi.pmc.ids.test.Helper.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import static org.hamcrest.Matchers.containsInAnyOrder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import gov.ncbi.pmc.ids.IdSet;
import gov.ncbi.pmc.ids.IdSet.IdFilter;
import gov.ncbi.pmc.ids.IdSet.IdScope;
import gov.ncbi.pmc.ids.IdDb;
import gov.ncbi.pmc.ids.IdNonVersionSet;
import gov.ncbi.pmc.ids.IdVersionSet;
import gov.ncbi.pmc.ids.IdType;
import gov.ncbi.pmc.ids.Identifier;


public class TestIdSet
{
    @Rule
    public TestName name = new TestName();

    /**
     * Setup function to initialize a small tree of ID sets
     */
    private static IdNonVersionSet _init() {
        IdNonVersionSet parent = (new IdNonVersionSet(litIds))
            .add(pmid.id("123456"),
                 pmcid.id("654321"),
                 doi.id("10.13/23434.56"));

        (new IdVersionSet(litIds, parent, false))
            .add(
                pmid.id("123456.1"),
                pmcid.id("654321.2"));
        (new IdVersionSet(litIds, parent, true))
            .add(
                pmid.id("123456.3"),
                mid.id("NIHMS77876"));
        (new IdVersionSet(litIds, parent, false))
            .add(
                pmcid.id("654321.8"), aiid.id("654343"));
        return parent;
    }

    // For verification purposes, when necessary
    private List<Identifier> _parentIds = Arrays.asList(
        pmid.id("123456"), pmcid.id("654321"), doi.id("10.13/23434.56"));
    private List<Identifier> _kid0Ids = Arrays.asList(
        pmid.id("123456.1"), pmcid.id("654321.2"));
    private List<Identifier> _kid1Ids = Arrays.asList(
        pmid.id("123456.3"), mid.id("NIHMS77876"));
    private List<Identifier> _kid2Ids = Arrays.asList(
        pmcid.id("654321.8"), aiid.id("654343"));


    // This lets us use lambdas in the _checkIds helper function
    interface IdGetter {
        Identifier get(IdType t);
    }

    // Helper to check all of the id types for an operation on a set
    private void _checkIds(IdGetter idGetter, String... expectedIds) {
        ArrayList<IdType> types =
                new ArrayList<>(Arrays.asList(pmid, pmcid, doi, mid, aiid));
        for (String idStr : expectedIds) {
            IdType type = types.remove(0);
            // If the idStr == null, then expectedId will equal null.
            Identifier expectedId = type.id(idStr);
            Identifier actualId = idGetter.get(type);
            if (expectedId == null) assertNull(actualId);
            else assertEquals(expectedId, actualId);
        }
    }

    private void _checkIdStream(Logger log, IdDb iddb,
            Stream<Identifier> actual, String... expected)
    {
        if (log != null) log.debug("_checkIdStream:");
        Iterator<Identifier> iter = actual.iterator();
        log.debug("Checking for " + expected.length + " ids");
        IntStream.range(0, expected.length)
            .forEach(i -> {
                Identifier expId = iddb.id(expected[i]);
                log.debug("  expected: " + expId);
                assertTrue(iter.hasNext());
                Identifier actualId = iter.next();
                log.debug("  actual: " + actualId);
                if (log != null) log.debug("  Comparing Identifiers " + expId + " and " + actualId);
                assertEquals(expId, actualId);
            });
    }


    /**
     * Test the constructor and factory methods - non-versioned
     * sets.
     */
    // passing
    @Test
    public void testConstructorNoVer()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet idg;

        idg = new IdNonVersionSet(litIds);
        assertFalse(idg.isVersionSpecific());
        assertEquals(0, idg.getVersions().size());
        assertNull(idg.getCurrentVersion());

        Identifier pmidId = pmid.id("123456");
        Identifier pmcidId = pmcid.id("654321");
        Identifier doiId = doi.id("10.12/23/45");
        idg.add(pmidId, pmcidId, doiId);

        List<Identifier> ids = idg.getIds();
        assertEquals(3, ids.size());
        assertThat(ids, containsInAnyOrder(pmidId, pmcidId, doiId));

        // test that adding the same id again does nothing
        ids.add(pmcidId);
        assertEquals(3, idg.getIds().size());

        // test that you can't add another id of the same type
        boolean exceptionThrown = false;
        try {
            idg.add(pmcid.id("87656"));
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        // verify that you can't add an id with wrong version-specificity
        exceptionThrown = false;
        try {
            idg.add(mid.id("NIHMS88756"));
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        log.info(testName + " done.");
    }

    /**
     * Test the constructor and factory methods of a version-specific IdSet.
     */
    @Test
    public void testConstructorVer()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = new IdNonVersionSet(litIds);
        IdVersionSet kid = new IdVersionSet(litIds,parent, false);
        assertTrue(kid.isVersionSpecific());
        assertTrue(kid.getParent() == parent);
        assertFalse(kid.isCurrent());

        Identifier pmidId = pmid.id("123456.1");
        Identifier pmcidId = pmcid.id("654321.2");
        Identifier midId = mid.id("NIHms12345");
        kid.add(pmidId, pmcidId, midId);

        List<Identifier> ids = kid.getIds();
        assertEquals(3, ids.size());
        assertThat(ids, containsInAnyOrder(pmidId, pmcidId, midId));

        // test that adding the same id again does nothing
        kid.add(pmcidId);
        assertEquals(3, kid.getIds().size());

        // test that you can't add another id of the same type
        boolean exceptionThrown = false;
        try {
            kid.add(pmcid.id("87656.2"));
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        // verify that you can't add an id with wrong version-specificity
        exceptionThrown = false;
        try {
            kid.add(doi.id("10.13/23434.56"));
        }
        catch (IllegalArgumentException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        log.info(testName + " done.");
    }

    /**
     * Test some of the basic methods linking non-version-specific parents and
     * version-specific children.
     */
    @Test
    public void testParentsAndKids()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdSet kid0 = parent.getVersions().get(0);
        IdSet kid1 = parent.getVersions().get(1);
        IdSet kid2 = parent.getVersions().get(2);

        log.debug("IdNonVersionSet parent: " + parent.toString());
        log.debug("IdVersionSet kid0: " + kid0.toString());
        log.debug("IdVersionSet kid1: " + kid1.toString());
        log.debug("IdVersionSet kid2: " + kid2.toString());

        assertFalse(parent.isVersionSpecific());
        assertTrue(kid0.isVersionSpecific());

        assertSame(kid1, parent.getComplement());
        assertNull(kid0.getComplement());
        assertSame(parent, kid1.getComplement());
        assertNull(kid2.getComplement());

        assertSame(kid1, parent.getCurrentVersion());
        assertSame(kid1, kid0.getCurrentVersion());
        assertSame(kid1, kid1.getCurrentVersion());
        assertSame(kid1, kid2.getCurrentVersion());

        assertSame(parent, parent.getNonVersioned());
        assertSame(parent, kid0.getNonVersioned());
        assertSame(parent, kid1.getNonVersioned());
        assertSame(parent, kid2.getNonVersioned());

        log.info(testName + " done.");
    }

    // passing
    @SuppressWarnings("unchecked")
    @Test
    public void testAllKidsReversed()
        throws Exception
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdSet kid0 = parent.getVersions().get(0);
        IdSet kid1 = parent.getVersions().get(1);
        IdSet kid2 = parent.getVersions().get(2);
        List<IdSet> glist;

        Method _allKidsReversed = privateMethod("ids.IdNonVersionSet", "_allKidsReversed");
        glist = ((Stream<IdVersionSet>) _allKidsReversed.invoke(parent))
                .collect(Collectors.toList());
        assertSame(kid2, glist.get(0));
        assertSame(kid1, glist.get(1));
        assertSame(kid0, glist.get(2));

        log.info(testName + " done.");
    }

    /**
     * Test the IdNonVersionSet::getKidsStream() method.
     */
    //-------------------------------------
    // passing
    @Test
    public void testGetKidsStream()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdVersionSet kid0 = parent.getVersions().get(0);
        IdVersionSet kid1 = parent.getVersions().get(1);
        IdVersionSet kid2 = parent.getVersions().get(2);
        List<IdVersionSet> glist;

        glist = parent.getKidsStream()
            .collect(Collectors.toList());
        assertEquals(3, glist.size());
        assertSame(kid1, glist.get(0));
        assertSame(kid2, glist.get(1));
        assertSame(kid0, glist.get(2));

        log.info(testName + " done.");
    }

    /**
     * Test the IdNonVersionSet::_workSetStream method, which implements
     * getSetStream(WORK).
     */
    @Test
    public void testWorkSetStream_NV() {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdVersionSet kid0 = parent.getVersions().get(0);
        IdVersionSet kid1 = parent.getVersions().get(1);
        IdVersionSet kid2 = parent.getVersions().get(2);

        List<IdSet> glist0 = parent._workSetStream().collect(Collectors.toList());
        assertEquals(4, glist0.size());
        assertSame(parent, glist0.get(0));
        assertSame(kid1, glist0.get(1));
        assertSame(kid2, glist0.get(2));
        assertSame(kid0, glist0.get(3));

        List<IdSet> glist1 = parent.getWorkSetStream().collect(Collectors.toList());
        assertEquals(glist0, glist1);

        log.info(testName + " done.");
    }

    /**
     * Test the IdVersionSet::_workSetStream method, which implements
     * getSetStream(WORK).
     */
    @Test
    public void testWorkSetStream_Ver() {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdVersionSet kid0 = parent.getVersions().get(0);
        IdVersionSet kid1 = parent.getVersions().get(1);
        IdVersionSet kid2 = parent.getVersions().get(2);
        List<IdSet> glist0;
        List<IdSet> glist1;

        glist0 = kid0._workSetStream().collect(Collectors.toList());
        assertEquals(4, glist0.size());
        assertSame(kid0, glist0.get(0));
        assertSame(parent, glist0.get(1));
        assertSame(kid1, glist0.get(2));
        assertSame(kid2, glist0.get(3));
        glist1 = kid0.getWorkSetStream().collect(Collectors.toList());
        assertEquals(glist0, glist1);

        glist0 = kid1._workSetStream().collect(Collectors.toList());
        assertEquals(4, glist0.size());
        assertSame(kid1, glist0.get(0));
        assertSame(parent, glist0.get(1));
        assertSame(kid2, glist0.get(2));
        assertSame(kid0, glist0.get(3));
        glist1 = kid1.getWorkSetStream().collect(Collectors.toList());
        assertEquals(glist0, glist1);

        glist0 = kid2._workSetStream().collect(Collectors.toList());
        assertEquals(4, glist0.size());
        assertSame(kid2, glist0.get(0));
        assertSame(parent, glist0.get(1));
        assertSame(kid1, glist0.get(2));
        assertSame(kid0, glist0.get(3));
        glist1 = kid2.getWorkSetStream().collect(Collectors.toList());
        assertEquals(glist0, glist1);

        log.info(testName + " done.");
    }

    /**
     * This tests making Streams of IdSets, in each of the three "scopes"
     * (IDENTITY, INSTANCE, and WORK) for a very simple case: just one
     * non-version-specific IdSet.
     */
    @Test
    public void testGetSetStream1()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = new IdNonVersionSet(litIds);
        Identifier pmidId = pmid.id("123456");
        Identifier pmcidId = pmcid.id("654321");
        Identifier doiId = doi.id("10.12/23/45");
        parent.add(pmidId, pmcidId, doiId);

        Stream<IdSet> gs;
        List<IdSet> glist;

        // identity, instance, and work are all the same - there is
        // only one
        gs = parent.getSetStream(IDENTITY);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assert(parent == glist.get(0));

        gs = parent.getSetStream(INSTANCE);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assert(parent == glist.get(0));

        gs = parent.getSetStream(WORK);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assert(parent == glist.get(0));

        log.info(testName + " done.");
    }

    /**
     * This tests making Streams of IdSets, in each of the three "scopes"
     * (IDENTITY, INSTANCE, and WORK) for a cluster.
     */
    @Test
    public void testGetSetStream2()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdSet kid0 = parent.getVersions().get(0);
        IdSet kid1 = parent.getVersions().get(1);
        IdSet kid2 = parent.getVersions().get(2);
        Stream<IdSet> gs;
        List<IdSet> glist;

        gs = parent.getSetStream(IDENTITY);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assertSame(parent, glist.get(0));

        gs = parent.getSetStream(INSTANCE);
        glist = gs.collect(Collectors.toList());
        assertEquals(2, glist.size());
        assertSame(parent, glist.get(0));
        assertSame(kid1, glist.get(1));

        gs = parent.getSetStream(WORK);
        glist = gs.collect(Collectors.toList());
        assertEquals(4, glist.size());
        assertSame(parent, glist.get(0));
        assertSame(kid1, glist.get(1));
        assertSame(kid2, glist.get(2));
        assertSame(kid0, glist.get(3));

        // kid0
        gs = kid0.getSetStream(IDENTITY);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assertSame(kid0, glist.get(0));

        gs = kid0.getSetStream(INSTANCE);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assertSame(kid0, glist.get(0));

        gs = kid0.getSetStream(WORK);
        glist = gs.collect(Collectors.toList());
        assertEquals(4, glist.size());
        assertSame(kid0, glist.get(0));
        assertSame(parent, glist.get(1));
        assertSame(kid1, glist.get(2));
        assertSame(kid2, glist.get(3));

        // kid1
        gs = kid1.getSetStream(IDENTITY);
        glist = gs.collect(Collectors.toList());
        assertEquals(1, glist.size());
        assertSame(kid1, glist.get(0));

        gs = kid1.getSetStream(INSTANCE);
        glist = gs.collect(Collectors.toList());
        assertEquals(2, glist.size());
        assertSame(kid1, glist.get(0));
        assertSame(parent, glist.get(1));

        gs = kid1.getSetStream(WORK);
        glist = gs.collect(Collectors.toList());
        assertEquals(4, glist.size());
        assertSame(kid1, glist.get(0));
        assertSame(parent, glist.get(1));
        assertSame(kid2, glist.get(2));
        assertSame(kid0, glist.get(3));

        log.info(testName + " done.");
    }



    /**
     * Test stream methods for the IDENTITY scope.
     */
    @Test
    public void testStreamIdentity() {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdSet kid0 = parent.getVersions().get(0);
        IdSet kid1 = parent.getVersions().get(1);
        IdSet kid2 = parent.getVersions().get(2);

        _checkIdStream(log, litIds,
            parent.getIdStream(parent.new IdFilter()),
            "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            kid0.getIdStream(kid0.new IdFilter()),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            kid1.getIdStream(kid1.new IdFilter()),
            "123456.3", "NIHMS77876");
        _checkIdStream(log, litIds,
            kid2.getIdStream(kid2.new IdFilter()),
            "PMC654321.8");

        _checkIdStream(log, litIds,
            parent.getIdStream(),
            "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            kid0.getIdStream(),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            kid1.getIdStream(),
            "123456.3", "NIHMS77876");
        _checkIdStream(log, litIds,
            kid2.getIdStream(),
            "PMC654321.8");

        List<IdType> types = new ArrayList<>(Arrays.asList(
            pmid, pmcid, doi, mid, aiid));

        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                parent.getIdStream(parent.new IdFilter(type))
            ),
            "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid0.getIdStream(parent.new IdFilter(type))
            ),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid1.getIdStream(parent.new IdFilter(type))
            ),
            "123456.3", "NIHMS77876");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid2.getIdStream(parent.new IdFilter(type))
            ),
            "PMC654321.8");


        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                parent.getIdStream(parent.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid0.getIdStream(parent.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid1.getIdStream(parent.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "123456.3", "NIHMS77876");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid2.getIdStream(parent.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "PMC654321.8");


        log.info(testName + " done.");
    }

    /**
     * Test getId(IDENTITY); in its various forms
     */
    @Test
    public void testGetIdIdentity() {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdSet kid0 = parent.getVersions().get(0);
        IdSet kid1 = parent.getVersions().get(1);
        IdSet kid2 = parent.getVersions().get(2);

        // .getId() with no qualifier could pick any of the valid IDs.
        log.debug("parent.getId(): " + parent.getId());
        assertTrue(_parentIds.contains(parent.getId()));
        log.debug("kid0.getId(): " + kid0.getId());
        assertTrue(_kid0Ids.contains(kid0.getId()));
        log.debug("kid1.getId(): " + kid1.getId());
        assertTrue(_kid1Ids.contains(kid1.getId()));
        log.debug("kid2.getId(): " + kid2.getId());
        assertTrue(_kid2Ids.contains(kid2.getId()));

        _checkIds(type -> parent.getId(IDENTITY, parent.new IdFilter(type)),
            "123456", "PMC654321", "10.13/23434.56", null, null);
        _checkIds(type -> kid0.getId(IDENTITY, kid0.new IdFilter(type)),
            "123456.1", "654321.2", null, null, null);
        _checkIds(type -> kid1.getId(IDENTITY, kid1.new IdFilter(type)),
            "123456.3", null, null, "NIHMS77876", null);
        _checkIds(type -> kid2.getId(IDENTITY, kid2.new IdFilter(type)),
            null, "654321.8", null, null, "654343");

        _checkIds(type -> parent.getId(parent.new IdFilter(type)),
            "123456", "PMC654321", "10.13/23434.56", null, null);
        _checkIds(type -> kid0.getId(kid0.new IdFilter(type)),
            "123456.1", "654321.2", null, null, null);
        _checkIds(type -> kid1.getId(kid1.new IdFilter(type)),
            "123456.3", null, null, "NIHMS77876", null);
        _checkIds(type -> kid2.getId(kid2.new IdFilter(type)),
            null, "654321.8", null, null, "654343");

        _checkIds(type -> parent.getId(type),
            "123456", "PMC654321", "10.13/23434.56", null, null);
        _checkIds(type -> kid0.getId(type),
            "123456.1", "654321.2", null, null, null);
        _checkIds(type -> kid1.getId(type),
            "123456.3", null, null, "NIHMS77876", null);
        _checkIds(type -> kid2.getId(type),
            null, "654321.8", null, null, "654343");

        _checkIds(type -> parent.getId(id -> id.getType().equals(type)),
            "123456", "PMC654321", "10.13/23434.56", null, null);
        _checkIds(type -> kid0.getId(id -> id.getType().equals(type)),
            "123456.1", "654321.2", null, null, null);
        _checkIds(type -> kid1.getId(id -> id.getType().equals(type)),
            "123456.3", null, null, "NIHMS77876", null);
        _checkIds(type -> kid2.getId(id -> id.getType().equals(type)),
            null, "654321.8", null, null, "654343");

        log.info(testName + " done.");
    }

    /**
     * Test stream getters in the INSTANCE scope
     */
    @Test
    public void testStreamInstance() {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        IdSet kid0 = parent.getVersions().get(0);
        IdSet kid1 = parent.getVersions().get(1);
        IdSet kid2 = parent.getVersions().get(2);

        _checkIdStream(log, litIds,
            parent.getInstanceIdStream(parent.new IdFilter()),
            "123456", "PMC654321", "10.13/23434.56", "123456.3", "NIHMS77876");
        _checkIdStream(log, litIds,
            kid0.getInstanceIdStream(kid0.new IdFilter()),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            kid1.getInstanceIdStream(kid1.new IdFilter()),
            "123456.3", "NIHMS77876", "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            kid2.getInstanceIdStream(kid2.new IdFilter()),
            "PMC654321.8", "aiid:654343");

        _checkIdStream(log, litIds,
            parent.getInstanceIdStream(),
            "123456", "PMC654321", "10.13/23434.56", "123456.3", "NIHMS77876");
        _checkIdStream(log, litIds,
            kid0.getInstanceIdStream(),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            kid1.getInstanceIdStream(),
            "123456.3", "NIHMS77876", "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            kid2.getInstanceIdStream(),
            "PMC654321.8", "aiid:654343");

        List<IdType> types = new ArrayList<>(Arrays.asList(
            pmid, pmcid, doi, mid, aiid));

        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                parent.getInstanceIdStream(parent.new IdFilter(type))
            ),
            "123456", "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid0.getInstanceIdStream(kid0.new IdFilter(type))
            ),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid1.getInstanceIdStream(kid1.new IdFilter(type))
            ),
            "123456.3", "123456", "PMC654321", "10.13/23434.56");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid2.getInstanceIdStream(kid2.new IdFilter(type))
            ),
            "PMC654321.8", "aiid:654343");

        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                parent.getInstanceIdStream(parent.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "123456", "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid0.getInstanceIdStream(kid0.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "123456.1", "PMC654321.2");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid1.getInstanceIdStream(kid1.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "123456.3", "123456", "PMC654321", "10.13/23434.56", "NIHMS77876");
        _checkIdStream(log, litIds,
            types.stream().flatMap(type ->
                kid2.getInstanceIdStream(kid2.new IdFilter(
                    id -> id.getType().equals(type)
                ))
            ),
            "PMC654321.8", "aiid:654343");

        log.info(testName + " done.");
    }

    /**
     * Test getId(INSTANCE)
     */
    @Test
    public void testGetInstanceId()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        List<IdVersionSet> kids = parent.getVersions();
        IdSet kid0 = kids.get(0);
        IdSet kid1 = kids.get(1);
        IdSet kid2 = kids.get(2);

        _checkIds(type -> parent.getId(INSTANCE, parent.new IdFilter(type)),
            "123456", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid0.getId(INSTANCE, kid0.new IdFilter(type)),
            "123456.1", "PMC654321.2", null, null, null);
        _checkIds(type -> kid1.getId(INSTANCE, kid1.new IdFilter(type)),
            "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid2.getId(INSTANCE, kid2.new IdFilter(type)),
            null, "PMC654321.8", null, null, "654343");

        _checkIds(type -> parent.getInstanceId(parent.new IdFilter(type)),
            "123456", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid0.getInstanceId(kid0.new IdFilter(type)),
            "123456.1", "PMC654321.2", null, null, null);
        _checkIds(type -> kid1.getInstanceId(kid1.new IdFilter(type)),
            "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid2.getInstanceId(kid2.new IdFilter(type)),
            null, "PMC654321.8", null, null, "654343");

        _checkIds(type -> parent.getInstanceId(type),
            "123456", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid0.getInstanceId(type),
            "123456.1", "PMC654321.2", null, null, null);
        _checkIds(type -> kid1.getInstanceId(type),
            "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid2.getInstanceId(type),
            null, "PMC654321.8", null, null, "654343");

        _checkIds(type -> parent.getInstanceId(id -> id.getType().equals(type)),
            "123456", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid0.getInstanceId(id -> id.getType().equals(type)),
            "123456.1", "PMC654321.2", null, null, null);
        _checkIds(type -> kid1.getInstanceId(id -> id.getType().equals(type)),
            "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876", null);
        _checkIds(type -> kid2.getInstanceId(id -> id.getType().equals(type)),
            null, "PMC654321.8", null, null, "654343");

        _checkIds(type -> parent.getInstanceId(type, true),
            "123456.3", null, null, "NIHMS77876", null);
        _checkIds(type -> kid0.getInstanceId(type, true),
            "123456.1", "PMC654321.2", null, null, null);
        _checkIds(type -> kid1.getInstanceId(type, true),
            "123456.3", null, null, "NIHMS77876", null);
        _checkIds(type -> kid2.getInstanceId(type, true),
            null, "PMC654321.8", null, null, "654343");

        _checkIds(type -> parent.getInstanceId(type, false),
            "123456", "PMC654321", "10.13/23434.56", null, null);
        _checkIds(type -> kid0.getInstanceId(type, false),
            null, null, null, null, null);
        _checkIds(type -> kid1.getInstanceId(type, false),
            "123456", "PMC654321", "10.13/23434.56", null, null);
        _checkIds(type -> kid2.getInstanceId(type, false),
            null, null, null, null, null);

        log.info(testName + " done.");
    }

    /**
     * Test getWorkId()
     */
    @Test
    public void testWorkId()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent = _init();
        List<IdVersionSet> kids = parent.getVersions();
        IdSet kid0 = kids.get(0);
        IdSet kid1 = kids.get(1);
        IdSet kid2 = kids.get(2);

        _checkIds(type -> parent.getWorkId(type),
            "123456", "PMC654321", "10.13/23434.56", "NIHMS77876", "654343"
        );
        _checkIds(type -> kid0.getWorkId(type),
            "123456.1", "PMC654321.2", "10.13/23434.56", "NIHMS77876", "654343"
        );
        _checkIds(type -> kid1.getWorkId(type),
            "123456.3", "PMC654321", "10.13/23434.56", "NIHMS77876", "654343"
        );
        _checkIds(type -> kid2.getWorkId(type),
            "123456", "PMC654321.8", "10.13/23434.56", "NIHMS77876", "654343"
        );

        log.info(testName + " done.");
    }

    private void _logSetCompare(Logger log, String scope,
        String label0, IdSet set0, String label1, IdSet set1)
    {
        log.debug("Same " + scope + "?");
        log.debug("    " + label0 + ": " + set0);
        log.debug("    " + label1 + ": " + set1);
    }

    /**
     * Test equals()
     */
    @Test
    public void testEquals() {
        String testName = name.getMethodName();
        Logger log = setup(TestIdSet.class, testName);

        IdNonVersionSet parent0 = _init();
        List<IdVersionSet> kids0 = parent0.getVersions();
        IdSet kid00 = kids0.get(0);
        IdSet kid01 = kids0.get(1);
        IdSet kid02 = kids0.get(2);

        IdNonVersionSet parent1 =
            (new IdNonVersionSet(litIds))
                .add(pmcid.id("654321"));
        IdVersionSet kid10 =
            (new IdVersionSet(litIds, parent1, false))
                .add(pmcid.id("654321.2"));
        IdVersionSet kid11 =
            (new IdVersionSet(litIds, parent1, true))
                .add(mid.id("NIHMS77876"));

        assertTrue(parent0.sameId(parent1));
        assertNotEquals(parent0, kid10);
        assertNotEquals(parent0, kid11);
        assertNotEquals(kid00, parent1);
        _logSetCompare(log, "id", "kid00", kid00, "kid10", kid10);
        assertTrue(kid00.sameId(kid10));
        assertNotEquals(kid00, kid11);
        assertNotEquals(kid01, parent1);
        assertNotEquals(kid01, kid10);
        _logSetCompare(log, "id", "kid01", kid01, "kid11", kid11);
        assertTrue(kid01.sameId(kid11));

        _logSetCompare(log, "instance", "parent0", parent0, "parent1", parent1);
        assertTrue(parent0.sameInstance(parent1));
        assertFalse(parent0.sameInstance(kid10));
        assertTrue(parent0.sameInstance(kid11));
        assertFalse(kid00.sameInstance(parent1));
        assertTrue(kid00.sameInstance(kid10));
        assertFalse(kid00.sameInstance(kid11));
        _logSetCompare(log, "instance", "kid01", kid01, "parent1", parent1);
        assertTrue(kid01.sameInstance(parent1));
        assertFalse(kid01.sameInstance(kid10));
        assertTrue(kid01.sameInstance(kid11));

        log.info(testName + " done.");
    }
}
