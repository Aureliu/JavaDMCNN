// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;

import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;

/**
 *  an Ace Event, with information from the ACE key or system output.
 */

public class AceEvent {

	/**
	 *  the type of the event:
	 */
	public String type;
	/**
	 *  the subtype of the event
	 */
	public String subtype;

	public String modality = "Asserted";
	public String polarity = "Positive";
	public String genericity = "Specific";
	public String tense = "Past";

	/**
	 *  the ID of the event
	 */
	public String id;
	/**
	 *  arguments of the event (each of type AceEventArgument)
	 */
	public ArrayList<AceEventArgument> arguments = new ArrayList<AceEventArgument>();
	/**
	 *  a list of the mentions of this event (each of type AceEventMention)
	 */
	public ArrayList<AceEventMention> mentions = new ArrayList<AceEventMention>();

  /**
   *  create a new event with the specified id, type, subtype, and arguments.
   */

	public AceEvent (String id, String type, String subtype) {
		this.id = id;
		this.type = type;
		this.subtype = subtype;
	}

	/**
	 *  create an AceEvent from the information in the APF file.
	 *  @param eventElement the XML element from the APF file containing
	 *                       information about this entity
	 *  @param acedoc  the AceDocument of which this AceEvent is a part
	 *  @param fileText  the text of the document
	 */

	public AceEvent (Element eventElement, AceDocument acedoc, String fileText) {
			id = eventElement.getAttribute("ID");
			type = eventElement.getAttribute("TYPE");
			subtype = eventElement.getAttribute("SUBTYPE");
			modality = eventElement.getAttribute("MODALITY");
			polarity = eventElement.getAttribute("POLARITY");
			genericity = eventElement.getAttribute("GENERICITY");
			tense = eventElement.getAttribute("TENSE");
			// record arguments
			NodeList arguments = eventElement.getElementsByTagName("event_argument");
			for (int j=0; j<arguments.getLength(); j++) {
				Element argumentElement = (Element) arguments.item(j);
				AceEventArgument argument = new AceEventArgument (argumentElement, acedoc);
				addArgument(argument);
			}
			NodeList mentionElements = eventElement.getElementsByTagName("event_mention");
			for (int j=0; j<mentionElements.getLength(); j++) {
				Element mentionElement = (Element) mentionElements.item(j);
				AceEventMention mention = new AceEventMention (mentionElement, acedoc, fileText);
				addMention(mention);
			}
	}

	void setId (String id) {
		this.id = id;
	}

	/**
	 *  add mention 'mention' to the event.
	 */

	public void addMention (AceEventMention mention) {
		mentions.add(mention);
	}

	/**
	 *  add argument 'argument' to the event.
	 */

	void addArgument (AceEventArgument argument) {
		arguments.add(argument);
	}

	/**
	 *  write the event to 'w' in APF format.
	 */

	public void write (PrintWriter w) {
		w.print   ("  <event ID=\"" + id + "\" TYPE=\"" + type + "\"");
		if (subtype != null && !subtype.equals(""))
		  w.print (" SUBTYPE=\"" + subtype + "\"");
		w.print (" MODALITY=\"" + modality + "\"");
		w.print (" POLARITY=\"" + polarity + "\"");
		w.print (" GENERICITY=\"" + genericity + "\"");
		w.print (" TENSE=\"" + tense + "\"");
		w.println (">");
		for (int i=0; i<arguments.size(); i++) {
			AceEventArgument argument = (AceEventArgument) arguments.get(i);
			argument.write(w);
		}
		for (int i=0; i<mentions.size(); i++) {
			AceEventMention mention = (AceEventMention) mentions.get(i);
			mention.write(w);
		}
		w.println ("  </event>");
	}

	public String toString () {
		StringBuffer buf = new StringBuffer();
		buf.append("event ");
		buf.append(type);
		buf.append(".");
		buf.append(subtype);
		buf.append("{");
		for (int i=0; i<mentions.size(); i++) {
			AceEventMention mention = (AceEventMention) mentions.get(i);
			buf.append(mention.toString());
		}
		buf.append("} ");
		return buf.toString();
	}
}
