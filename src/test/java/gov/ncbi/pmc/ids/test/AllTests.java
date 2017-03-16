package gov.ncbi.pmc.ids.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
    TestIdDb.class,
    TestIdentifier.class,
    TestIdSet.class,
    TestIdResolver.class,
    TestIdType.class,
    TestRequestId.class
})

public class AllTests {}
