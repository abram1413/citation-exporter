<schema xmlns='http://purl.oclc.org/dsdl/schematron'>
  <pattern>
    <rule context='ObjectNode'>
      <assert test='source = "PMC"'/>
      <assert test='count(accessed/date-parts) = 3'/>
      <assert test='publisher-place = "San Francisco, USA"'/>
    </rule>
  </pattern>
</schema>
