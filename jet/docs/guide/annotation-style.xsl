<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="annotation-list">
		<html>
		<h1>Jet Annotations</h1>
		<xsl:apply-templates/>
		</html>
	</xsl:template>
	<xsl:template match="annotation">
		<h3> <font color="red">
		<xsl:value-of select="type"/>
		</font></h3>
		<xsl:value-of select="description"/>
		<br/><br/><b>Annotated by:</b>
		<xsl:value-of select="annotator"/>
		<br/>
		<xsl:if test="feature">
		<br/><b>Features:</b>
		<table border="1" cellpadding="5">
			<tr><td>Name</td><td>Value</td><td>Description</td></tr>
		<xsl:for-each select="feature">
			<tr>
			<td>
			<xsl:apply-templates select="name"/>
			</td>
			<td>
			<xsl:apply-templates select="value"/>
			</td>
			<td>
			<xsl:apply-templates select="description"/>
			</td>
			<!-- still have to record feature annotator, if present -->
			</tr>
		</xsl:for-each>
		</table>
		</xsl:if>
		<xsl:apply-templates select="table-note"/>
	</xsl:template>
	<xsl:template match="i">
		<i><xsl:apply-templates/></i>
	</xsl:template>
	<xsl:template match="br">
		<br/>
	</xsl:template>
</xsl:stylesheet>