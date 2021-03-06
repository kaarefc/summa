<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:Index="http://statsbiblioteket.dk/summa/2008/Document" xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns:xalan="http://xml.apache.org/xalan" xmlns:java="http://xml.apache.org/xalan/java" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" exclude-result-prefixes="java xs xalan xsl" version="1.0" xsi:schemaLocation="http://www.openarchiv">
    <xsl:output version="1.0" encoding="UTF-8" indent="yes" method="xml"/>
    <xsl:template name="notes">
        <Index:field Index:name="no" Index:disabled_boost="7">
            <xsl:value-of select="beskrivelse"/>
        </Index:field>
        <Index:field Index:name="no" Index:disabled_boost="7">
            <xsl:value-of select="titel"/>
        </Index:field>
    </xsl:template>
</xsl:stylesheet>
