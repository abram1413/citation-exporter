package gov.ncbi.pmc.ids.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.pmc.ids.IdDb;
import gov.ncbi.pmc.ids.IdSet;
import gov.ncbi.pmc.ids.IdType;
import gov.ncbi.pmc.ids.Identifier;
import gov.ncbi.pmc.ids.RequestId;

public class Helper {
    public static final IdDb litIds = IdDb.litIds;
    public static final IdType pmid = litIds.getType("pmid");
    public static final IdType pmcid = litIds.getType("pmcid");
    public static final IdType mid = litIds.getType("mid");
    public static final IdType doi = litIds.getType("doi");
    public static final IdType aiid = litIds.getType("aiid");

    /**
     * Call this first from each test method, and it will initialize the
     * logger, and log the test name. (Make sure to instantiate a new Logger
     * for each method -- don't reuse them.)
     *
     * To configure the logger, change a property either in
     * src/test/resources/log4j.properties, or on the command-line with the
     * `-D` switch.
     *
     * Example boilerplate for a test:
     *
     * <pre><code>public class MyTest {
     *     {@literal @}Rule
     *     public TestName name = new TestName();
     *
     *     {@literal @}Test
     *     public void testMethod() {
     *         String testName = name.getMethodName();
     *         Logger log = Helper.setup(TestMyTest.class, testName);
     *
     *         ...
     *
     *         log.info(testName + " done.");
     *     }
     * }</code></pre>
     */
    @SuppressWarnings("rawtypes")
    public static Logger setup(Class testClass, String testName, String[][] props)
    {
        // First set default system properties for a few important ones, only if they
        // are not set already.
        setDefaultSystemProperties(new String[][] {
            {"item_source", "test"},
            {"item_source_id_type", "aiid"}
        });

        // Then set "overrides"
        setSystemProperties(props);

        Logger log = LoggerFactory.getLogger(testClass);
        log.info("############### " + testName + " ###############");
        return log;
    }

    /**
     * A convenience form of setup, for use when no default system properties need
     * to be overridden.
     */
    @SuppressWarnings("rawtypes")
    public static Logger setup(Class testClass, String testName)
    {
        return setup(testClass, testName, null);
    }

    /**
     * For a list of system properties, this sets the default values: i.e.,
     * it sets the property value if and only if that property is not
     * already set.
     */
    public static void setDefaultSystemProperties(String[][] props)
    {
        if (props == null) return;
        for (String[] pair : props) {
            String key = pair[0];
            String val = pair[1];
            if (System.getProperty(key) == null) {
                System.setProperty(key, val);
            }
        }
    }

    /**
     * Sets a list of system properties.
     */
    public static void setSystemProperties(String[][] props) {
        if (props != null) {
            for (String[] pair : props) {
                System.setProperty(pair[0], pair[1]);
            }
        }
    }

    /**
     * This opens up a private method, using reflection, so that you can do some
     * unit testing on it.
     */
    public static Method privateMethod(String className, String methodName)
            throws ClassNotFoundException, NoSuchMethodException
    {
        Method method = Class.forName("gov.ncbi.pmc." + className)
                .getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method;
    }



    /**
     * Helper function to check an Identifier's type and (canonical) value.
     * For example:
     *
     *     import static gov.ncbi.pmc.ids.test.Helper.checkId;
     *     ...
     *     checkId("message", pmcid, "PMC134455", "pmcid:PMC134455", false, id);
     */
    public static void checkId(String msg,
            IdType expType, String expValue, String expCurie,
            boolean expIsVersioned, Identifier id)
    {
        assertEquals(msg + "; type", expType, id.getType());
        assertEquals(msg + "; value", expValue, id.getValue());
        assertEquals(msg + "; curie", expCurie, id.getCurie());
        assertEquals(msg + "; isVersioned", expIsVersioned, id.isVersionSpecific());
    }

    /**
     * Helper to check a RequestId. Example:
     *
     *     RequestId rid = new RequestId(iddb, H.pmcid, "pMc1234");
     *     H.checkRequestId("pMc1234", H.pmcid, "PMC1234", rid);
     */
    public static void checkRequestId(String expReqVal, IdType expMainType,
            String expMainValue, RequestId rid)
    {
        assertEquals(expReqVal, rid.getRequestedValue());
        assertEquals(expMainType, rid.getMainType());
        assertEquals(expMainValue, rid.getMainValue());
    }

    /**
     * Check that two IdSets have the exact same identifiers for
     * each of the five literature types
     */
    public static void checkIdSet(IdSet expected, IdSet actual) {
        assertEquals(expected.getId(pmid), actual.getId(pmid));
        assertEquals(expected.getId(pmcid), actual.getId(pmcid));
        assertEquals(expected.getId(doi), actual.getId(doi));
        assertEquals(expected.getId(mid), actual.getId(mid));
        assertEquals(expected.getId(aiid), actual.getId(aiid));

    }
    /**
     *  Helper class to wrap an IdSet. This lets you make repeated
     *  assertions on it.
     */
    public static class SetChecker {
        private IdSet _g;
        public SetChecker(IdSet g) { _g = g; }

        /**
         * Verify that the IdSet has an Identifier matching the expected curie,
         * given the IdType t. These can be chained. E.g.:
         *
         *     H.SetChecker gc = new H.SetChecker(mySet);
         *     gc.check("pmid:1234", H.pmid)
         *       .check("pmcid:PMC3435", H.pmcid);
         */
        public SetChecker check(String expectCurie, IdType t) {
            Identifier id = _g.getId(t);
            if (expectCurie == null)
                assertNull(id);
            else
                assertEquals(expectCurie, id.toString());
            return this;
        }

        /**
         * Verify that the resource referred to by this IdSet is the same work
         * as the Identifier argument.
         */
        public SetChecker sameWork(String expectCurie, IdType type) {
            Identifier id = _g.getWorkId(type);
            if (expectCurie == null)
                assertNull(id);
            else {
                assert(id != null);
                assertEquals(expectCurie, id.toString());
            }
            return this;
        }
    }
}
