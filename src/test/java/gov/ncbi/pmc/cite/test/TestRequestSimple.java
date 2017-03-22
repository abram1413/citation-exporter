package gov.ncbi.pmc.cite.test;

import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;

import gov.ncbi.pmc.cite.App;
import gov.ncbi.pmc.cite.Request;

/**
 * Simple unit test for Requests, illustrating how to use Mockito. The
 * data-driven version of this is in TestRequests.java.
 */
public class TestRequestSimple {
    protected App app;
    @SuppressWarnings("unused")
    private Logger log;

    @Rule
    public TestName name = new TestName();

    @Before
    public void setup()  throws Exception
    {
        log = Utils.setup(name);
    }

    /**
     * Test the request object. This is a simple manual test using Mockito,
     * to show how it works.
     */
   @Ignore
    @Test
    public void testRequestSimple() throws Exception
    {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        when(req.getParameter("id")).thenReturn("30");
        when(req.getParameter("idtype")).thenReturn("aiid");

        StringWriter page = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(page));

        Request r = new Request(req, resp);
        r.doGet();
        verify(resp).setContentType(eq("text/html;charset=UTF-8"));
        verify(resp).setCharacterEncoding(eq("UTF-8"));
        verify(resp).setStatus(eq(200));
        assertThat(page.toString(), matchesPattern(".*aiid:30.*"));
    }
}
