package gov.ncbi.pmc.ids.test;

import static gov.ncbi.pmc.ids.IdDb.litIds;
import static gov.ncbi.pmc.ids.IdType.CType.NOOP;
import static gov.ncbi.pmc.ids.IdType.CType.REPLACEMENT;
import static gov.ncbi.pmc.ids.IdType.CType.UPPERCASE;
import static gov.ncbi.pmc.ids.test.Helper.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import gov.ncbi.pmc.ids.IdType;
import gov.ncbi.pmc.ids.IdType.Checker;

public class TestIdType {
    @Rule
    public TestName name = new TestName();

    /**
     * Test the IdType constructors and getters.
     */
    @Test
    public void testConstructAndGetters()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdType.class, testName);
        IdType pmcid;

        pmcid = new IdType(litIds, "pmcid", new ArrayList<Checker>(Arrays.asList(
            new Checker("^(\\d+)$", REPLACEMENT, "PMC$1", false),
            new Checker("^(\\d+(\\.\\d+)?)$", REPLACEMENT, "PMC$1", true),
            new Checker("^([Pp][Mm][Cc]\\d+)$", UPPERCASE, null, false),
            new Checker("^([Pp][Mm][Cc]\\d+(\\.\\d+)?)$", UPPERCASE, null, true)
        )));

        assertEquals("pmcid", pmcid.getName());
        List<Checker> checkers = pmcid.getCheckers();
        assertEquals(4, checkers.size());
        assertEquals(Pattern.compile("^(\\d+(\\.\\d+)?)$").toString(),
                checkers.get(1).getPattern().toString());

        assert(pmcid.matches("667387"));
        assertEquals("PMC667387", pmcid.id("667387").getValue());

        assert(pmcid.matches("PMC667388376"));
        assertEquals("PMC667388376", pmcid.id("PMC667388376").getValue());

        assert(pmcid.matches("pMc3"));
        assertEquals("PMC3", pmcid.id("pMc3").getValue());

        assertSame(litIds, pmcid.getIdDb());

        log.info(testName + " done.");
    }

    @Test
    public void testCheckers()
    {
        String testName = name.getMethodName();
        Logger log = setup(TestIdType.class, testName);

        String pat0 = "^(\\d+(\\.\\d+)?)$";
        String repl0 = "PMC$1";
        IdType diamond = new IdType(litIds, "diamond", new ArrayList<>(Arrays.asList(
            new Checker(pat0, REPLACEMENT, repl0, true),
            new Checker("^([Pp][Mm][Cc]\\d+(\\.\\d+)?)$", UPPERCASE, null, true)
        )));

        Checker c0 = diamond.getCheckers().get(0);
        assertEquals(REPLACEMENT, c0.getCType());
        assertEquals(pat0, c0.getPattern().pattern());
        assertEquals(repl0, c0.getReplacement());
        assertTrue(c0.isVersioned());
        log.debug("Checker c0: " + c0.toString());
        assertEquals("'^(\\d+(\\.\\d+)?)$' -> 'PMC$1', versioned", c0.toString());

        Checker c1 = diamond.getCheckers().get(1);
        assertEquals(UPPERCASE, c1.getCType());

        log.info(testName + " done.");
    }

    @Test
    public void testCheckerEquals()
    {
        Checker x = new Checker("^fo(o.*)$", REPLACEMENT, "$1", false);
        assertTrue(x.equals(x));
        assertFalse(x.equals(null));

        Checker y = new Checker("^fo(o.*)$", REPLACEMENT, "$1", false);
        assertTrue(y.equals(y));
        assertFalse(y.equals(null));

        assertNotSame(x, y);
        assertTrue(x.equals(y));
        assertTrue(y.equals(x));
        assertEquals(x.hashCode(), y.hashCode());

        assertFalse(x.equals("PMC778476"));
        Stream.of(
                new Checker("^foo(o.*)$", REPLACEMENT, "$1", false),
                new Checker("^fo(o.*)$", NOOP, null, false),
                new Checker("^fo(o.*)$", REPLACEMENT, "$12", false),
                new Checker("^fo(o.*)$", REPLACEMENT, "$1", true)
        ).forEach(chk -> {
            assertFalse(x.equals(chk));
            assertFalse(chk.equals(x));
            assertFalse(y.equals(chk));
            assertFalse(chk.equals(y));
        });
}

    @Test(expected=IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBadChecker0() {
        IdType blech = new IdType(litIds, "blech", new ArrayList<>(Arrays.asList(
            new Checker("^(\\d+)$", NOOP, "PMC$1", false)
        )));
    }

    @Test(expected=IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBadChecker1() {
        IdType blech = new IdType(litIds, "blech", new ArrayList<>(Arrays.asList(
            new Checker("^(\\d+)$", REPLACEMENT, null, false)
        )));
    }

    @Test(expected=IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBadName0() {
        IdType foo = new IdType(litIds, "Foo",
            new ArrayList<Checker>(Arrays.asList()));
    }

    @Test(expected=IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBadName1() {
        IdType foo = new IdType(litIds, "|foo",
            new ArrayList<Checker>(Arrays.asList()));
    }

    @Test(expected=IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBadName2() {
        IdType foo = new IdType(litIds, "foFo",
            new ArrayList<Checker>(Arrays.asList()));
    }

    @Test(expected=IllegalArgumentException.class)
    @SuppressWarnings("unused")
    public void testBadName3() {
        IdType foo = new IdType(litIds, "fo|o",
            new ArrayList<Checker>(Arrays.asList()));
    }
}
