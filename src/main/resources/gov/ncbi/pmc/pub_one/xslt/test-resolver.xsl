<?xml version="1.0" encoding="UTF-8"?>

<!--
  This is for testing that we can do transformations that require external
  resources: an included XSLT.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  exclude-result-prefixes="xs"
  version="2.0">

  <xsl:include href="dates-and-strings.xsl"/>

  <xsl:template match="element()">
    <xsl:copy>
      <xsl:apply-templates select="@*,node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="attribute()|text()|comment()|processing-instruction()">
    <xsl:copy/>
  </xsl:template>

</xsl:stylesheet>
