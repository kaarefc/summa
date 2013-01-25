<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        >
    <xsl:output method="html" encoding="UTF-8"/>

    <xsl:param name="query"/>
    <xsl:param name="filter"/>
    <xsl:param name="sort"/>
    <xsl:param name="reverse"/>
    <xsl:param name="per_page"/>
    <xsl:param name="current_page"/>

    <xsl:template match="/responsecollection/response/documentresult">
        <xsl:choose>
            <xsl:when test="@hitCount=0">
                No hits
            </xsl:when>
            <xsl:otherwise>

                <div class="shortNavigation">
                    <xsl:choose>
                        <xsl:when test="$current_page &gt; 0">
                            <a href="?query={$query}&amp;filter={$filter}&amp;sort={$sort}&amp;reverse={$reverse}&amp;page={$current_page - 1}">Previous</a>
                        </xsl:when>
                        <xsl:otherwise>
                            Previous
                        </xsl:otherwise>
                    </xsl:choose>
                    /
                    <xsl:choose>
                        <xsl:when test="$current_page &lt; (ceiling(@hitCount div $per_page) - 1)">
                            <a href="?query={$query}&amp;filter={$filter}&amp;sort={$sort}&amp;reverse={$reverse}&amp;page={$current_page + 1}">Next</a>
                        </xsl:when>
                        <xsl:otherwise>
                            Next
                        </xsl:otherwise>
                    </xsl:choose>
                    (<xsl:value-of select="@hitCount" />)
                </div>
                <div class="searchResult">
<!--                    <div class="searchResultItemHeader searchResultItemStatus">
                        &#160;
                    </div>-->
                    <div class="searchResultItemHeader searchResultItemTitle">
                        Title
                    </div>
                    <div class="searchResultItemHeader searchResultItemAuthor">
                        Author
                    </div>
                    <div class="searchResultItemHeader searchResultItemYear">
                        Year
                    </div>
                    <div class="searchResultItemHeader searchResultItemType">
                        Type
                    </div>
                    <div class="searchResultItemHeader searchResultItemScore">
                        Score
                    </div>
                </div>


                <xsl:for-each select="record">
                    <xsl:for-each select="field[@name='shortformat']">
                        <xsl:for-each select="shortrecord/RDF/Description">
                            <xsl:call-template name="showfields-classic" />
                        </xsl:for-each>
                    </xsl:for-each>

                    <xsl:for-each select="field[@name='summa:explain']">
                        <xsl:call-template name="explain" />
                    </xsl:for-each>
                </xsl:for-each>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template name="explain">
        <div class="searchResultItemCommon searchResultItemExpandedQuery">
            <p>Expanded query: <span class="expandedQueryText">
                <xsl:value-of select="explanation/expandedquery"/>
            </span></p>
        </div>
        <div class="searchResultItemCommon searchResultItemExplanation">
            <p>Score:</p>
            <pre>
                <xsl:value-of select="explanation/score"/>
            </pre>
        </div>
    </xsl:template>

    <xsl:template name="showfields-classic">
        <xsl:variable name="record_id" select="../../../../field[@name='recordID']"/>
        <xsl:variable name="recordBase" select="../../../../field[@name='recordBase']"/>
        <xsl:variable name="score" select="../../../../@score"/>

        <xsl:variable name="mattype" select="type[@lang='da']"/>
        <xsl:variable name="mattype_en" select="type[@lang='en']"/>

        <!-- author variable -->


        <!-- title variable -->
        <xsl:variable name="maintitle">
            <xsl:if test="title">
                <xsl:choose>
                    <xsl:when test="contains(title,' : ')">

                        <xsl:value-of select="substring-before(title,' : ')"/>

                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="title"/>

                    </xsl:otherwise>
                </xsl:choose>
            </xsl:if>
        </xsl:variable>

        <xsl:variable name="subtitle">
            <xsl:if test="contains(title,' : ')">
                <xsl:text> : </xsl:text>
                <xsl:value-of select="substring-after(title,' : ')"/>

            </xsl:if>
        </xsl:variable>
        <!-- year variable -->
        <xsl:variable name="year">
            <xsl:if test="date">
                <xsl:value-of select="date"/>
            </xsl:if>
        </xsl:variable>

        <!-- title identifier -->
        <xsl:variable name="identifier">
            <xsl:if test="identifier">
                <xsl:value-of select="identifier"/>
            </xsl:if>
        </xsl:variable>

        <!-- outputting result -->
        <div id="item_{$record_id}" class="searchResultItem">
            <div id="explanation{position()}" style="display:none;">
                <xsl:text>&#160;</xsl:text>
            </div>

            <!--title-->
            <div class="searchResultItemCommon searchResultItemTitle">
                <a href="showrecord.jsp?record_id={$record_id}">
                    <xsl:value-of select="$maintitle"/>
                </a>
                <xsl:value-of select="$subtitle"/>
                <xsl:text> </xsl:text>
            </div>

            <!-- author -->
            <div class="searchResultItemCommon searchResultItemAuthor">
                <xsl:value-of select="normalize-space(creator)"/>
            </div>

            <!-- year -->
            <div class="searchResultItemCommon searchResultItemYear">
                <xsl:value-of select="$year"/>
                <xsl:text> </xsl:text>
            </div>

            <!-- type of material -->
            <div class="searchResultItemCommon searchResultItemType">
                <xsl:choose>
                    <xsl:when test="contains($identifier,'http:')">
                        <!-- TODO: Use <xsl:value-of select="$mattype"/> if available -->
                        <a href="{$identifier}">  <xsl:value-of select="$recordBase"/>  </a>
                    </xsl:when>
                    <xsl:otherwise>
                        <!--<xsl:value-of select="$mattype"/>-->
                        <span class="searchResultItemRecordBase"><xsl:value-of select="$recordBase"/></span>
                    </xsl:otherwise>
                </xsl:choose>
                <xsl:text> </xsl:text>
            </div>

            <!-- score -->
            <div class="searchResultItemCommon searchResultItemScore">
                <xsl:value-of select="$score"/>
            </div>
        </div>
    </xsl:template>

</xsl:stylesheet>
