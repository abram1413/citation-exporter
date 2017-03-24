<?xml version="1.0" encoding="UTF-8"?>

<!--
  This is just the identity XSLT with a new name. It's invoked with the
  specified output ("report") format of "text/plain", in order to get the
  string value (the text nodes) of the XML.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  exclude-result-prefixes="xs"
  version="2.0">

  <xsl:template match="element()">
    <xsl:copy>
      <xsl:apply-templates select="@*,node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="attribute()|text()|comment()|processing-instruction()">
    <xsl:copy/>
  </xsl:template>

</xsl:stylesheet>
