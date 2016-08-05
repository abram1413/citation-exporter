This is a citation exporting web service, based on the following excellent
open-source tools:

* [citeproc-java](https://github.com/michel-kraemer/citeproc-java).
* [citeproc-js](http://gsl-nagoya-u.net/http/pub/citeproc-doc.html)


## Quick start

Clone this repository:

```
git clone https://github.com/ncbi/citation-exporter.git
```

Then build and run this web service:

```
mvn test
mvn jetty:run
```

Point your browser to
[http://localhost:11999/samples](http://localhost:11999/samples).


## Testing

Run unit tests as follows:

```
mvn test
```

To run just one specific set of tests:

```
mvn -Dtest=AppTest test
```

You can use wildcards; for example:

```
mvn '-Dtest=*Test' test
```

See documentation on the [Maven Surefire
plugin](http://maven.apache.org/surefire/maven-surefire-plugin/examples/single-test.html)
for more options.


### Test samples

A good set of samples is listed in the application's [samples
page](https://www.ncbi.nlm.nih.gov/pmc/utils/ctxp/samples).


### Data-driven validation unit tests

Among the unit tests that `mvn test` will run are two sets that use data files
to verify the output of transformations, and the responses to requests:
TestTransforms.java and TestRequests.java.

***TestTransforms.java***

This provides data-driven schematron and regular-expression matching tests
of the XSLT transforms. The individual test cases are defined in the
[transform-tests.json](src/test/resources/transform-tests.json) file, which
is read into a List of TransformTestCase objects.

You can use the `test_cases` system property to select which specific test
case to run:

- If omitted, or empty, all tests are run
- Otherwise, it's matched against the description, as a regular expression

So, for example, to test all the cases that have "PubOne" in the description,
run:

```
mvn -Dtest=TestTransforms -Dtest_cases=PubOne test
```

The format of the transform-tests.json file is defined in comments there.

When checking XML output, Schematron files are used, that are also in
the src/test/resources directory.

***TestRequests.java***

This provides unit tests for the Request class, which handles HTTP requests.
It uses Mockito to mock HttpServletRequest and HttpServletResponse objects.
It reads test cases from the
[request-tests.json](src/test/resources/request-tests.json) file into a List
of RequestTestCase objects.

As with TestTransforms, you can use the `test_cases` property to
select which tests to run.

For example, to test all the cases that have "style" in the description,
run:

```
mvn -Dtest=TestRequests -Dtest_cases=style test
```


## Running as executable jar with embedded Jetty

```
mvn package
java -jar target/pmc-citation-exporter-*.jar
```

As before, go to
[http://localhost:11999/samples](http://localhost:11999/samples) in
your browser to see the results.


## Configuration

Configuration is controlled with system properties.
Set these on the run command line, for example:

```
mvn jetty:run -Djetty.port=9876 -Dcache_ids=true -Did_cache_ttl=8
```

Here are some of the parameters that can be used:

* `cache_ids` - either "true" or "false".  Default is "false".
* `com.sun.management.jmxremote.authenticate` - Set this to "false" to turn on
  turn on the [JMX monitor
  console](http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html),
  without user authentication. Note that this is only suitable for a
  development environment.
* `com.sun.management.jmxremote.port` - Set this to a port number, if you
  want to use the [JMX monitor
  console](http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html).
* `id_cache_ttl` - time-to-live for each of the IDs in the ID cache, in
  seconds. Default is 86400.
* `id_converter_params` - Query string parameters to send to the the PMC ID
  converter API.  Default is
  "showaiid=yes&format=json&tool=ctxp&email=pubmedcentral@ncbi.nlm.nih.gov".
* `id_converter_url` - URL of the PMC ID converter API.  Default is
  "https://www.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/".
* `item_source` - string specifying which ItemSource to use.  The default is
  "test", which indicates to use the TestItemSource, which loads data items
  from files in the class path. If not "test", then the value should be the
  fully qualified name of the class. Possible values are:
    * gov.ncbi.pmc.cite.StcachePubOneItemSource - requires item_source_loc to
      also be set
    * gov.ncbi.pmc.cite.StcacheNxmlItemSource - requires item_source_loc to
      also be set
    * gov.ncbi.pmc.cite.ConvAppNxmlItemSource - Get NXML from an HTTP web
      service. Requires item_source_loc to also be set
* `item_source_loc` - When item_source is one of the Stcache options, this
  needs to be the full pathname of the stcache image file.  When item_source
  is ConvAppNxmlItemSource, then this should be the URL of the converter app
  service.
* `java.io.tmpdir` - used when running as an "uber jar"; this is where the
  application is unpacked.
* `jetty.port` - the IP port number that the service will listen on
* `log` - location of the log files.  Defaults to the *log* subdirectory of
  the directory from which the app is run.
* `log_level` - sets the logging level for the root logger, as one of the
  [log4j levels](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Level.html);
  e.g. "DEBUG", "INFO". By default, this is `DEBUG` when running from the
  jetty maven plugin, and `INFO` when running as an "uber jar".
* `xml.catalog.files` - used by the Apache commons CatalogResolver; this is
  the pathname of the OASIS catalog file to use when parsing XML files. See
  below for more info. Default value is "catalog.xml"


### DTDs and XML catalog files

The repository comes with an OASIS catalog file, [catalog.xml](catalog.xml),
that is used, by default, to find DTDs. This contains:

```xml
<nextCatalog catalog="catalog-local.xml"/>
<nextCatalog catalog="jats/catalog.xml"/>
```

This causes the resolver to try to resolve IDs from:

* catalog-local.xml, if it exists. If you create this file, then you can
  override any definitions from other catalogs.
* jats/catalog.xml, if it exists. This file is included in the repository,
  and you can use the [jats/get-dtds.sh](jats/get-dtds.sh) script to download
  the corresponding DTDs from the JATS site. (This is executed by default
  in a Maven build).

If the JATS (and other) DTDs are located somewhere else on your system, then
there are two ways to override the default behavior.

1.  Set the `xml.catalog.files` system property, to point to some other
    master catalog file. For example:

        mvn test -Dxml.catalog.files=/pmc/load/catalog/linux-oxygen-pmc3-catalog.xml

2.  Create a *catalog-local.xml* file in the root directory of the repo, and
    override specific DTDs there.


## API

### Special URLs

The following two URLs are special:

* /samples - provides a list of links to sample outputs of various document,
   in various formats
* /errortest - strictly for testing, this causes the application to generate
  an error page
* /echotest - for testing, causes the application to echo a very simple text
  page
* /info - some status information

### Parameters:

* **id** or **ids** - List of IDs, comma-delimited. The types and expected
  patterns of the values given here are the same as for
  the [PMC ID converter
  API](https://www.ncbi.nlm.nih.gov/pmc/tools/id-converter-api/).
  The type can either be specified explicitly with the idtype parameter, or
  can be inferred. IDs are always resolved to one of `aiid` or `pmid`.
* **idtype** - Specifies the type of the IDs given in the ids parameter.
  Any of these types is allowed:
    * pmcid - includes versioned ids
    * pmid
    * mid
    * doi
* **report** - Specifies the embedded format of the data.  Defaults to "html".
  See the table below for allowed values.
* **format** - corresponds to the returned "content-type". Can be used as
  substitute for content negotiation.  Default depends on report; see the
  table below.
* **style** or **styles** - CSL style name, or a list of stylenames.  If
  just one ID is given, this can include multiple names, comma-delimited.
  If multiple IDs are given, then this must be only one style name. In
  other words, you can have multiple IDs or multiple styles, but not both.
  Defaults to "modern-language-association".

Value combinations of report and format are listed in the following table.

```
report           format  Comments
------           ------  --------
html             html    Styled citations in raw HTML format. Same as citeproc-node.
ris              ris     Machine-readable citation, in RIS format. Media type is application/x-research-info-systems.
nbib             nbib    Machine-readable citation, in NBIB (MEDLINE) format. Media type is application/nbib.
citeproc         json    Machine-readable citation, in citeproc-json format.
pub-one or pub1  xml     New unified literature format.
```

### Error responses

If everything goes well, the service will return a status of 200, of course.
If there is a problem, one of the following codes will be returned:

* 400 Bad request - if the request parameters can't be deciphered, or similar
  problems
* 404 Not found - for IDs that are of the correct form, but can't be found in
  the data
* 500 Internal server error - if there is problem with an upstream service,
  like the ID converter, or a runtime exception in this service's software.

### Styled citation responses

Each styled citation will be in it's own `<div>` element, which will have
some special attributes to identify it:

* **data-id** - the ID used in the request for the resource.  This will have
  a type prefix, followed by a colon, and then the ID value.  E.g. pmdi
* **data-style** - the name of the citation style
* **data-resolved-id** - optional, if the requested ID doesn't match the
  resolved ID.

The individual record `<div>`s will be wrapped in an outer `<div>` element.
If there were requested IDs that couldn't be resolved, then they will be
listed in the **data-not-found** attribute on this outer `<div>`.

For example, a single record styled in a single citation style (request
"?ids=PMC3000436"):

```html
<div>
  <div class="csl-entry" data-id="aiid:3000436"
    data-style="modern-language-association">Barash,
    Uri et al. “Proteoglycans in Health and Disease: New Concepts for Heparanase Function in Tumor
    Progression and Metastasis.” <i>FEBS J</i> 277.19 (2010): n. pag.</div>
</div>
```

A response for a request for multiple IDs, one of which cannot be resolved
(request "?ids=PMC3000436,PMC99999999,PMC3155436"):

```html
<div data-not-found="pmcid:PMC99999999">
  <div class="csl-entry" data-id="pmcid:PMC3000436"
    data-resolved-id="aiid:3000436"
    data-style="modern-language-association">Barash, Uri et al. “Proteoglycans in Health
    and Disease: New Concepts for Heparanase Function in Tumor Progression and Metastasis.”
    <i>FEBS J</i> 277.19 (2010): n. pag.</div>
  <div class="csl-entry" data-id="pmcid:PMC3155436"
    data-resolved-id="aiid:3155436"
    data-style="modern-language-association">“Correction.” <i>Can Fam Physician</i> 57.8
    (2011): 879–879. Print.</div>
</div>
```


### PubOne format responses

The response for a single record in PubOne format will look something like
the following (request "?ids=PMC3000436&report=pub1"):

```xml
<pub-one-record xmlns:mml="http://www.w3.org/1998/Math/MathML"
  xmlns:xlink="http://www.w3.org/1999/xlink"
  record-type="article" xml:lang="en">
  <source-meta>
    <object-id pub-id-type="nlm-journal-id">101229646</object-id>
    ...
  </source-meta>
  <document-meta>
    <object-id pub-id-type="doi">10.1111/j.1742-4658.2010.07799.x</object-id>
    <object-id pub-id-type="manuscript">nihpa226684</object-id>
    <object-id pub-id-type="pmcid">3000436</object-id>
    <object-id pub-id-type="pmid">20840586</object-id>
    ...
  </document-meta>
</pub-one-record>
```

If more than one record is returned, they will be wrapped in an outer
`<pub-one-records>` element, and each identified with attributes in the
namespace "http://www.ncbi.nlm.nih.gov/ns/search". For example, a response
with for two good IDs and one bad one (request
"?ids=PMC3000436,PMC99999999,PMC3155436&report=pub1"):

```xml
<pub-one-records xmlns:s="http://www.ncbi.nlm.nih.gov/ns/search"
                 s:not-found="pmcid:PMC99999999">
  <pub-one-record s:id="pmcid:PMC3000436"
                  s:resolved-id="aiid:3000436" ...>...</pub-one-record>
  <pub-one-record s:id="pmcid:PMC3155436"
                  s:resolved-id="aiid:3155436" ...>...</pub-one-record>
</pub-one-records>
```

### citeproc-json format responses

The response for a single record in this format will be a JSON object.  For
example (request "?ids=PMC3000436&report=citeproc"):

```json
{
  "id": "aiid:3000436",
  "title": "Proteoglycans in health and disease: New concepts for
    heparanase function in tumor progression and metastasis",
  "author": [ ... ],
  ...
  "issue": "19",
  "PMID": "20840586",
  "PMCID": "3000436",
  "DOI": "10.1111/j.1742-4658.2010.07799.x",
}
```

Note that the value used for the `id` field is the resolved, canonical
identifier, and that the other forms of identifier appear in citeproc-json
specific fields `PMID`, `PMCID`, etc.

If there are multiple records, they will be wrapped in a JSON array.  For
example, the response with two good records and one bad one will look like
this (request ""):

```json
[
  {
    "not-found": "pmcid:PMC99999999"
  },
  {
    "id": "aiid:3000436",
    "title": "Proteoglycans in health and disease: New concepts for heparanase function in
      tumor progression and metastasis",
    ...
    "PMID": "20840586",
    "PMCID": "3000436",
    "DOI": "10.1111/j.1742-4658.2010.07799.x",
  },
  {
    "id": "aiid:3155436",
    "title": "Correction",
    ...
    "PMCID": "3155436",
  }
]
```


# Monitoring the application


## Logging

The location of log files is controlled by the system parameter `log`, which
is usually set to the value "log" using `-Dlog=log` command-line switch.

Logging is controlled by properties set in the
*src/main/resources/log4j.properties* file. The log level is controlled by
the `log4j.rootLogger` property, and can be set to one of TRACE, DEBUG, INFO,
WARN, ERROR or FATAL.


## JMX Monitor Console

You can use [JMX
monitoring](http://docs.oracle.com/javase/8/docs/technotes/guides/management/agent.html),
to check the service, either locally or remote.

When running with "jetty:run", *local* JMX monitoring is enabled by default.
After starting the server, from another terminal, enter the command `jconsole`,
and then open the connection to the "org.codehaus.plexus..." process that is
listed.

When running as an "uber jar", start it with the following options (for
example):

```
mvn package
java -Dcom.sun.management.jmxremote.port=11997 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar target/pmc-citation-exporter-*.jar
```

Then, you can connect to a monitor console by using the command:

```
jconsole localhost:11997
```


## Development

### Build environments

This repository has been configured such that, *by default*, it can run
stand-alone, without any dependencies on NCBI-internal libraries or services.

In the production environment, however, we require access to a Java library
which has not been released openly (groupId=gov.ncbi.pmc,
artifactId=pmc-lib). References to that library exist in two class files,
StcachePubOneItemSource and StcacheNxmlItemSource; so, *by default*, those
are excluded from compilation.  This is accomplished by making the default
build profile is
"dev", which explicitly excludes those two class files, and doesn't include
the dependency on the pmc-lib library.

Building for production is done with the use of the "prod" Maven profile
(`mvn -Pprod`). That profile doesn't exclude those class files for
compilation, and does declares the dependency on the pmc-lib library.


### Test item provider

When the value of *item_source" is "test", the citation data is mock data
from the *src/main/webapp/test* directory.

### Eclipse setup

To work in Eclipse, import this project into your workspace.

***Turn off validation of some subdirectories.***

Turn off validation of the DTD subdirectories of *jats*
by right-clicking on them, selecting
"Properties", and then check "derived". This will cause all of the files
under these directories to be excluded from validation. You might have
to select "Project" -> "Clean" to get rid of existing errors.

It might help even more, to speed up builds, to turn off all validation in the
project. Right click on the project, then select Properties, then Validation,
and check "Suspend all validators".

***Set up some file types***

In Preferences → General → Content Types, select "Text", then "XML". Then
add two new types: "*.nxml" (JATS PMC NXML format) and "*.sch" (Schematron
files).


### Jetty configuration

There are two ways to run the server under Jetty:

* Using the [Jetty Maven
  plugin](http://www.eclipse.org/jetty/documentation/current/jetty-maven-plugin.html),
  with `mvn jetty:run`. This is useful for development.
* As an executable "uber jar", created using the [Apache Maven Shade
  plugin](http://maven.apache.org/plugins/maven-shade-plugin/), with
  `mvn package; java -jar target/pmc-citation-exporter...jar`.


### Run with Jetty Maven plugin

To use the Jetty Maven plugin, run with, for example:

```
mvn clean jetty:run
```

This *does not use* the src/main/webapp/jetty.xml configuration file.

In this case, the main entry point to the app is in MainServlet.java;
the code in WebServer.java does not get executed.

Some options are set in the pom.xml so that, when running with `jetty:run`,
it scans for changes to
source files, and to automatically redeploys whenever any are detected. Also,
in the pom file, some default values are given for System properties that are
used to configure the app.

For example:

```xml
<!-- Jetty Maven plugin -->
<plugin>
  <groupId>org.eclipse.jetty</groupId>
  <artifactId>jetty-maven-plugin</artifactId>
  <version>${jetty.version}</version>
  <configuration>
    <scanIntervalSeconds>2</scanIntervalSeconds>
    <scanTargetPatterns>
      <scanTargetPattern>
        <directory>src/main/resources</directory>
        <includes>
          <scanTargetPatterns>**/*.*</scanTargetPatterns>
        </includes>
      </scanTargetPattern>
    </scanTargetPatterns>
    <systemProperties>
      <systemProperty>
        <name>jetty.port</name>
        <value>11999</value>
      </systemProperty>
      <systemProperty>
        <name>log</name>
        <value>log</value>
      </systemProperty>
      <systemProperty>
        <name>log_level</name>
        <value>DEBUG</value>
      </systemProperty>
    </systemProperties>
  </configuration>
</plugin>
```

But note that there seems to be a bug in the "hot redeploy" feature
of the Jetty Maven plugin. When changes are made to sample files under
src/main/resources/samples, the application is restarted, but for some reason,
those changed files are not copied into the target/classes/samples directory
as they should be. Those sample files *are* copied, however, when the
application restarts as a result of a Java source file changing.


### Jetty shaded uber-jar

This is used when you run the `mvn package` command, and causes the creation
of an executable jar file in the target subdirectory (currently
target/pmc-citation-exporter-\<version>.jar), that includes
all of the dependencies, including Jetty itself.

This is controlled by the [Apache Maven Shade
plugin](http://maven.apache.org/plugins/maven-shade-plugin/),
which is configured by a \<plugin> section of the pom.xml.

In addition, another plugin section, build-helper-maven-plugin, is required
to specify additional directories to copy into the target jar file.

To run the server from this executable jar, execute something like this

```
java -Djava.io.tmpdir=./jetty-temp-dir \
  -jar target/pmc-citation-exporter-*.jar
```

Note that system properties must be set on the command line *before* the
`-jar` option.

When running this way, Jetty is configured by the src/main/webapp/jetty.xml
file. (Note that this is *not used* when running with `mvn jetty:run`).
The main entry point to the application is in WebServer.java.


### Exception handling

Exception classes used when handling a request:

- CiteException
    - BadParamException - e.g. an id value that doesn't match one of the
      known patterns
    - NotFoundException - when an id is of the correct form, but not
      resolved, or the pub-one not found in the stcache
    - ServiceException - some problem with upstream service; results in 500
- java.io.IOException - from, for example, java xml processing, error
  creating CSL object, etc. Results in 500

When handling a request, the PrintWriter for the page is instantiated last.
If there is an exception when that is done, then the code just writes to the
error log and returns.

This is implemented with the pattern:

```java
resp.setContentType("text/html;charset=UTF-8");
resp.setCharacterEncoding("UTF-8");
resp.setStatus(HttpServletResponse.SC_OK);
initPage();
if (page == null) return;
```

So, this means that the whole page is prepared as a String first, and then
written out after the page is initialized.


## Dependencies

Dependencies are declared in the *pom.xml* file, and are resolved
automatically by Maven.

Below is a list of some of the stable dependencies, along with links to
documentation, useful when doing development, and more details, where
warranted.

Several of the dependencies use a free third-party service
[Jitpack.io](https://jitpack.io/) in order to freeze specific revisions
from GitHub repositories. These are libraries that are not on Maven
Central, and Jitpack.io provides a way to ensure that we are using a
stable version. This requires adding the following to the \<repositories>
section of the pom:

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>
```


### Java

Requires Java version 8.

* Platform / library [Javadocs](http://docs.oracle.com/javase/8/docs/api/)


### Saxon Home Edition

It uses Saxon-HE for XSLT tranformations.

* [Javadocs](http://www.saxonica.com/documentation/Javadoc/index.html)


### Jetty

* [Documentation](http://www.eclipse.org/jetty/documentation/9.2.1.v20140609/)
* [Javadocs](http://download.eclipse.org/jetty/stable-9/apidocs/)


### citeproc-java

The official source for this code is on GitHub at

* [michel-kraemer/citeproc-java](https://github.com/michel-kraemer/citeproc-java)
* [javadocs](http://michel-kraemer.github.io/citeproc-java/api/latest/)

The current version of citation exporter uses a fork of this at

* [klortho/citeproc-java, branch
  pmc-22661-ahead-of-print](https://github.com/klortho/citeproc-java/tree/pmc-22661-ahead-of-print)
* [javadocs](http://klortho.github.io/citeproc-java/api/3f30f220/)

It uses [Jitpack.io](https://jitpack.io/) to freeze the version at a
specific commit.

To use the latest development version of this library, rather than the
release package, clone the GitHub repository to your local machine.
Then (see the [build
instructions](http://michel-kraemer.github.io/citeproc-java/using/building/)
do the following to create the jar file, and install that in your local
Maven cache:

```
./gradlew install
```

In the citation-exporter pom, make sure you change the dependency to
use the "real" coordinates, instead of the ones from Jitpack.io:

```xml
<dependency>
  <!-- Use these coordinates for the jitpack.io version: -->
  <groupId>com.github.Klortho</groupId>
  <artifactId>citeproc-java</artifactId>
  <version>3f30f220c279557892d69c9ec95b92d84cf2114d</version>
  <!-- Use these when running from a local, development version:
    <groupId>de.undercouch</groupId>
    <artifactId>citeproc-java</artifactId>
    <version>0.7-SNAPSHOT</version> -->
</dependency>
```


### citeproc-js

* [Manual](http://gsl-nagoya-u.net/http/pub/citeproc-doc.html)

This is included by reference, from citeproc-java. The repository URL
and the exact commit are specified in the
[citeproc-java/build.gradle](https://github.com/Klortho/citeproc-java/blob/pmc-22661-ahead-of-print/citeproc-java/build.gradle)
file.

As of 12/10/2015, we are using a fork of this, on BitBucket at
[klortho/citeproc-js](https://bitbucket.org/klortho/citeproc-js), branch
[pmc-22661-epub-date](https://bitbucket.org/klortho/citeproc-js/branch/pmc-22661-epub-date).
Note that we've tried to update to a later version, that includes a fix
for [this
issue](https://bitbucket.org/fbennett/citeproc-js/issues/173/fix-institutional-authors-jiggery-pokery).
In [this
comment](https://bitbucket.org/fbennett/citeproc-js/issues/173/fix-institutional-authors-jiggery-pokery#comment-15831726),
the author indicates the fix is in version 1.0.559, but the API must have
changed, because updating citeproc-java to use [this
commit](https://bitbucket.org/klortho/citeproc-js/commits/b0dc750455dff9242a85131525496e92ce21de03?at=pmc-22661-epub-date)
causes its unit tests to break.



### Citation style language (CSL) libraries

These are two libraries that are used to format citations. The
originals are here:

* [citation-style-languages/styles](https://github.com/citation-style-language/styles)
* [citation-style-languages/locales](https://github.com/citation-style-language/locales)

***styles***

Currently (12/10/2015) we're using forks of these, at:

* [klortho/styles, branch pmc-22661-olf](https://github.com/Klortho/styles/tree/pmc-22661-olf), and
* [locales/styles, branch mavenize](https://github.com/Klortho/locales/tree/mavenize)

Changes to the styles repo include support of an epublication date
and an indication of ahead-of-print.

These branches also includes the addition of pom.xml files, so that they
can be brought in as Maven dependencies.

These use Jitpack.io to freeze the version at a specific commit.

In order to use a development versions of these, instead of the default
version, merely clone one or the other (or both). Then, in these working
copies of the repos, install the packages
to your local Maven cache with:

```
mvn install
```

Then, in the
citation-exporter pom, change the dependency (dependencies) to
use the "real" coordinates, instead of the ones from Jitpack.io.
For example,

```xml
<dependency>
  <!-- Use these coordinates for the jitpack.io version: -->
  <groupId>com.github.Klortho</groupId>
  <artifactId>styles</artifactId>
  <version>2b644a6c51743991d6d26b44961f9063dbd92c49</version>
  <!-- Use these when running from a local, development version:
    <groupId>org.citationstyles</groupId>
    <artifactId>styles</artifactId>
    <version>1.0.1-pmc-22661-olf</version> -->
</dependency>
```

Note that the official GitHub repos for these are not "mavenized":
they do not have pom files. Michel Kraemer provides a service that
mavenizes them automatically, and upload SNAPSHOT versions of them to
Sonatype's OSS repo daily, see
[here](https://oss.sonatype.org/#nexus-search;quick~org.citationstyles).


### PMC ID Converter API

* [Documentation](https://www.ncbi.nlm.nih.gov/pmc/tools/id-converter-api/)


### Jackson

We're using the Jackson library to read JSON objects. Here are some
handy links:

* [Home page](http://wiki.fasterxml.com/JacksonHome)
* [Data binding](https://github.com/FasterXML/jackson-databind) - includes
  tutorial on how to use it.
* [Javadocs](http://fasterxml.github.io/jackson-databind/javadoc/2.3.0/)
* [Jackson annotations](https://github.com/FasterXML/jackson-annotations) -
  how to annotate the classes that map to JSON objects


### kitty-cache

The library is on Google code
[here](https://code.google.com/p/kitty-cache/), and is also mirrored to
GitHub, at [treeder/kitty-cache](https://github.com/treeder/kitty-cache),
but it is not in Maven Central.

It is declared in the citation-exporter pom.xml, using
[JitPack.io](https://jitpack.io) to build and deploy it from a fork of the
repository on GitHub, at
[Klortho/kitty-cache](https://github.com/Klortho/kitty-cache).
(The reason for using a fork is to protect against the possibility of the
original repo being removed.)


### PubOne

This is a library of XSLTs that convert to and from NCBI's PubOne
format, and is on GitHub at
[klortho/pub-one](https://github.com/Klortho/pub-one).

We use  Jitpack.io to freeze the version and to make it available
from a Maven repository.

In order to use a development version of this, instead of the default
version, clone it to your local machine, and build and install the package
to your local Maven cache with:

```
mvn install
```

Then, in the
citation-exporter pom, change the dependency to
use the "real" coordinates, instead of the ones from Jitpack.io.
For example,

```xml
<dependency>
  <!-- Use these coordinates for the jitpack.io version: -->
  <groupId>com.github.Klortho</groupId>
  <artifactId>pub-one</artifactId>
  <version>v1.0.2</version>
  <!-- Use these when running from a local, development version:
    <groupId>gov.ncbi.pmc</groupId>
    <artifactId>pub-one</artifactId>
    <version>1.0.2</version> -->
</dependency>
```


# Public Domain notice

National Center for Biotechnology Information.

This software is a "United States Government Work" under the terms of the
United States Copyright Act.  It was written as part of the authors'
official duties as United States Government employees and thus cannot
be copyrighted.  This software is freely available to the public for
use. The National Library of Medicine and the U.S. Government have not
placed any restriction on its use or reproduction.

Although all reasonable efforts have been taken to ensure the accuracy
and reliability of the software and data, the NLM and the U.S.
Government do not and cannot warrant the performance or results that
may be obtained by using this software or data. The NLM and the U.S.
Government disclaim all warranties, express or implied, including
warranties of performance, merchantability or fitness for any
particular purpose.

Please cite NCBI in any work or product based on this material.


