// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;

import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;

/**
 *  an Ace event mention, with information from the ACE key.
 */

public class AceEventMention {

	/**
	 *  the ID of the mention
	 */
	public String id;
	/**
	 *  arguments of the event mention (each of type AceEventMentionArgument)
	 */
	public ArrayList<AceEventMentionArgument> arguments =
		new ArrayList<AceEventMentionArgument>();
	/**
	 *  the span of the extent of the event, with start and end positions based
	 *  on the ACE offsets (excluding XML tags).
	 */
	public Span extent;
	/**
	 *  the span of the extent of the event, with start and end positions based
	 *  on Jet offsets (and so including following whitespace).
	 **/
	public Span jetExtent;
	/**
	 *  the text of the extent of the event mention.
	 */
	public String text;
	/**
	 *  the span of the anchor of the event, with start and end positions based
	 *  on the ACE offsets (excluding XML tags).
	 */
	public Span anchorExtent;
	/**
	 *  the span of the anchor of the event, with start and end positions based
	 *  on Jet offsets (and so including following whitespace).
	 **/
	public Span anchorJetExtent;
	/**
	 *  the text of the anchor
	 */
	public String anchorText;
	/**
	 *  our confidence in the presence of this event mention
	 */
	public double confidence = 1.0;

	public AceEventMention (String id, Span jetExtent, Span anchorJetExtent, String fileText) {
		this.id = id;
		this.arguments = new ArrayList<AceEventMentionArgument>();
		this.extent = AceEntityMention.convertSpan(jetExtent, fileText);
		this.jetExtent = jetExtent;
		this.text = fileText.substring(this.extent.start(), this.extent.end()+1);
		this.anchorExtent = AceEntityMention.convertSpan(anchorJetExtent, fileText);
		this.anchorJetExtent = anchorJetExtent;
		this.anchorText = fileText.substring(this.anchorExtent.start(), this.anchorExtent.end()+1);
	}

	/**
	 *  create an AceEventMention from the information in the APF file.
	 *
	 *  @param mentionElement the XML element from the APF file containing
	 *                       information about this mention
	 *  @param acedoc        the AceDocument to which this relation mention
	 *                       belongs
	 */

	public AceEventMention (Element mentionElement, AceDocument acedoc, String fileText) {
		id = mentionElement.getAttribute("ID");
		NodeList extents = mentionElement.getElementsByTagName("extent");
		Element extentElement = (Element) extents.item(0);
		extent = AceEntityMention.decodeCharseq(extentElement);
		jetExtent = AceEntityMention.aceSpanToJetSpan(extent, fileText);
		text = fileText.substring(extent.start(), extent.end()+1);
		// Span jetExtent = AceEntityMention.aceSpanToJetSpan(extent, fileText);
		NodeList anchors = mentionElement.getElementsByTagName("anchor");
		Element anchorElement = (Element) anchors.item(0);
		anchorExtent = AceEntityMention.decodeCharseq(anchorElement);
		anchorText = fileText.substring(this.anchorExtent.start(), this.anchorExtent.end()+1);
		anchorJetExtent = AceEntityMention.aceSpanToJetSpan(anchorExtent, fileText);
		NodeList arguments = mentionElement.getElementsByTagName("event_mention_argument");
		for (int j=0; j<arguments.getLength(); j++) {
			Element argumentElement = (Element) arguments.item(j);
			AceEventMentionArgument argument = new AceEventMentionArgument (argumentElement, acedoc);
			addArgument(argument);
		}
	}

	void addArgument (AceEventMentionArgument argument) {
		arguments.add(argument);
	}

	void setId (String id) {
		this.id = id;
	}

	/**
	 *  write the APF representation of the event mention to <CODE>w</CODE>.
	 */
	 
	public void write (PrintWriter w) {
		w.print  ("    <event_mention ID=\"" + id + "\"");
		if (Ace.writeEventConfidence)
			w.format(" p=\"%5.3f\"", confidence);
		w.println(">");
		w.println("      <extent>");
		AceEntityMention.writeCharseq (w, extent, text);
		w.println("      </extent>");
		w.println("      <anchor>");
		AceEntityMention.writeCharseq (w, anchorExtent, anchorText);
		w.println("      </anchor>");
		for (int i=0; i<arguments.size(); i++) {
			AceEventMentionArgument argument = (AceEventMentionArgument) arguments.get(i);
			argument.write(w);
		}
		w.println("    </event_mention>");
	}

	public String toString () {
		StringBuffer buf = new StringBuffer();
		buf.append(anchorText);
		// buf.append("[" + text + "]"); // display extent
		buf.append("(");
		for (int i=0; i<arguments.size(); i++) {
			if (i > 0) buf.append(", ");
			AceEventMentionArgument argument = (AceEventMentionArgument) arguments.get(i);
			buf.append(argument.toString());
		}
		buf.append(") ");
		return buf.toString();
	}

}
