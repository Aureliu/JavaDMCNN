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
 *  an Ace Event Mention Argument, with information from the ACE key.
 */

public class AceEventMentionArgument {

	/**
	 *  the role of the argument in the event
	 */
	public String role;
	/**
	 *  the value of the argument:  an AceEntityMention or AceTimexMention
	 */
	public AceMention value;
	/**
	 *  our confidence in the presence of this argument
	 */
	public double confidence = 1.0;
	/**
	 *  our confidence in this role assignment for this argument
	 */
	public double roleConfidence = 1.0;

	public AceEventMentionArgument (AceMention value, String role) {
		this.value = value;
		this.role = role;
	}

	/**
	 *  create an AceEventMentionArgument from the information in the APF file.
	 *  @param argumentElement the XML element from the APF file containing
	 *                       information about this argument
	 *  @param acedoc  the AceDocument of which this AceEvent is a part
	 */

	public AceEventMentionArgument (Element argumentElement, AceDocument acedoc) {
			role = argumentElement.getAttribute("ROLE");
			String mentionid = argumentElement.getAttribute("REFID");
			value = acedoc.findMention(mentionid);
	}

	/**
	 *  write the APF representation of the event mention argument to <CODE>w</CODE>.
	 */
	 
	public void write (PrintWriter w) {
		w.print  ("      <event_mention_argument REFID=\"" + value.id + "\" ROLE=\"" + role + "\"");
		if (Ace.writeEventConfidence) {
			w.format(" p=\"%5.3f\"", confidence);
			w.format(" pRole=\"%5.3f\"", roleConfidence);
		}
		w.println("/>");
	}

	public String toString () {
		return role + ":" + ((value == null) ? "?" : value.getHeadText());
	}

	public boolean equals (Object o) {
		if (!(o instanceof AceEventMentionArgument))
			return false;
		AceEventMentionArgument p = (AceEventMentionArgument) o;
		return this.role.equals(p.role) &&
		       this.value.id.equals(p.value.id);
	}
}
