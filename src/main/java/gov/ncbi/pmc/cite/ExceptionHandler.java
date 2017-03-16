package gov.ncbi.pmc.cite;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/ExceptionHandler")
public class ExceptionHandler extends HttpServlet {
    private Logger log = LoggerFactory.getLogger(ExceptionHandler.class);
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws ServletException, IOException
    {
        processError(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
        throws ServletException, IOException
    {
        processError(request, response);
    }

    private void processError(HttpServletRequest request,
                              HttpServletResponse response)
        throws IOException
    {
        String logMessage = "Server error page: ";

        // Analyze the servlet exception
        Throwable throwable = (Throwable)
            request.getAttribute("javax.servlet.error.exception");
        Integer statusCode = (Integer)
            request.getAttribute("javax.servlet.error.status_code");
        String servletName = (String)
            request.getAttribute("javax.servlet.error.servlet_name");
        if (servletName == null) {
            servletName = "Unknown";
        }
        String requestUri = (String)
            request.getAttribute("javax.servlet.error.request_uri");
        if (requestUri == null) {
            requestUri = "Unknown";
        }

        // Set response content type
        response.setContentType("text/html");

        PrintWriter out = response.getWriter();
        out.write("<html>\n" +
                  "  <head>\n" +
                  "    <title>Citation Exporter Problem</title>\n" +
                  "  </head>\n" +
                  "  <body>\n");

        out.write("<h3>Server error</h2>\n" +
                  "<p>\n" +
                  "  Our apologies.  There seems to have been a problem " +
                  "fulfilling your request.\n" +
                  "  Please try again in a few minutes.\n" +
                  "</p>\n" +
                  "<p>\n" +
                  "  If the problem persists, please send an email to our " +
                  "help desk, at\n" +
                  "  <a href='mailto:pubmedcentral@ncbi.nlm.nih.gov'>" +
                  "pubmedcentral@ncbi.nlm.nih.gov</a>.\n" +
                  "</p>\n" +
                  "<h3>Details</h3>\n");

        if (statusCode != 500) {
            logMessage += "statusCode = " + statusCode + "; requestUri = '" +
                requestUri + "'";
            out.write("<p>\n" +
                "  <strong>Status Code</strong>: " + statusCode + "<br/>\n" +
                "  <strong>Requested URI</strong>: " + requestUri +
                "</p>\n");
        }
        else {
            logMessage += "servletName = '" + servletName +
                "'; requestUri = '" + requestUri + "'";
            out.write("<p>\n");
            out.write("  <strong>Servlet Name</strong>: " + servletName +
                "<br/>\n");
            out.write("  <strong>Requested URI</strong>: " + requestUri +
                "<br/>\n");
            if (throwable != null) {
                String exceptionName = throwable.getClass().getName();
                String message = throwable.getMessage();
                logMessage += "; exception name = '" + exceptionName +
                    "'; message = '" + message + "'";
                out.write("  <strong>Exception Name</strong>: " +
                    exceptionName + "<br/>\n" +
                    "  <strong>Message</strong>: " + message + "<br/>\n");
            }
            out.write("</p>\n");
        }
        out.write("  </body>\n" +
                  "</html>\n");

        log.error(logMessage);
        if (throwable != null) {
            log.error(throwable.getMessage(), throwable);
        }
    }
}
