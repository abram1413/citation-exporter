<?xml version="1.0" encoding="UTF-8"?>

<!--
  This stylesheet is used in testing, to make sure we can pass parameters in.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="2.0">
  <xsl:param name='param1'  select="'default param1 value'"/>
  <xsl:output method="text"/>

  <xsl:template match='/'>
    <xsl:value-of select='$param1'/>
  </xsl:template>

</xsl:stylesheet>
