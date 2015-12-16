package gov.ncbi.pmc.cite.test;

import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ncbi.pmc.cite.App;

/**
 * This provides a single function that does setup that is shared
 * by all the tests.
 */
public class TestSetup {

    public static void setProperties()
    {
        System.setProperty("log", "testlog");
        System.setProperty("log_level", "DEBUG");
    }

    /**
     * Call this first from your test method, and it will log the test name
     * first, and then initialize the App. Use it to set the test's log
     * variable at the same time. For example:
     *
     *   public class MyTest {
     *       private Logger log;
     *       @Rule
     *       public TestName name = new TestName();
     *       @Test
     *       public void testMethod() throws Exception {
     *           log = TestSetup.setup(name);
     *           ...
     *       }
     *   }
     */
    public static Logger setup(TestName name)
        throws Exception
    {
        setProperties();
        Logger log = LoggerFactory.getLogger(name.getClass());
        log.info("Starting test " + name.getMethodName());
        App.init();
        return log;
    }
}
