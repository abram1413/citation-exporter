package gov.ncbi.pmc.cite.test;

import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.ncbi.pmc.cite.App;
import gov.ncbi.pmc.cite.Request;

/**
 * Unit test for Requests. This uses Mockito to mock HttpServletRequest and
 * HttpServletResponse objects. It reads test cases from request-tests.json
 * into a list of RequestTestCase objects.
 *
 * You can use the `test_cases` system property to select which tests to run:
 * - If omitted, or empty, all tests are run
 * - Otherwise, it's matched against the description, as a regular expression
 *
 * So, to test all the cases that have "style" in the description, set:
 * -Dtest_cases=style
 */
@RunWith(value = Parameterized.class)
public class TestRequests {
    protected App app;
    @SuppressWarnings("unused")
    private Logger log;
    private RequestTestCase testCase;
    private static String selected_test_cases =
        System.getProperty("test_cases");

    @Rule
    public TestName name = new TestName();

    @Before
    public void setup()  throws Exception
    {
        log = Utils.setup(name);
    }

    // Parameter passed in via this constructor
    public TestRequests(RequestTestCase _testCase) {
        testCase = _testCase;
    }

    // This generates the parameters; reading them from
    // the JSON file
    @Parameters(name= "{index}: TestRequest: {0}")
    public static Collection<RequestTestCase> cases()
        throws Exception
    {
        // Read the request-tests.json file
        ObjectMapper mapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        URL testCasesUrl = TestRequests.class.getClassLoader()
            .getResource("request-tests.json");
        List<RequestTestCase> testCaseList =
            mapper.readValue(testCasesUrl.openStream(),
                new TypeReference<List<RequestTestCase>>() {});

        // Filter the test based on the test_case system property
        return testCaseList.stream()
            .filter(p ->
                selected_test_cases == null ||
                selected_test_cases.equals("") ||
                p.description.matches("(.*)" + selected_test_cases + "(.*)")
            ).collect(Collectors.toList());
    }

    /**
     * Test one test case from the JSON file
     */
    @Ignore
    @Test
    public void testRequestTestCases() throws Exception
    {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        // Set the values that the mock request object will
        // return for the query-string parameters
        for (Map.Entry<String, String> param :
             testCase.requestParams.entrySet())
        {
            when(req.getParameter(param.getKey()))
              .thenReturn(param.getValue());
        }

        // Create a StringWriter to hold the response content
        StringWriter page = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(page));

        // This is what we are testing
        Request r = new Request(req, resp);
        r.doGet();

        // Verify the results
        RequestTestCase.Expect expect = testCase.expect;
        verify(resp).setStatus(eq(expect.status));
        verify(resp).setContentType(eq(expect.contentType));
        verify(resp).setCharacterEncoding(eq(expect.characterEncoding));
        String content = page.toString();
        for (String pattern : expect.patterns) {
            Pattern p = Pattern.compile("^.*" + pattern + ".*$",
                    Pattern.DOTALL);
            assertThat(content, matchesPattern(p));
        }
    }
}
