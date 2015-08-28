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
 *  an Ace relation mention, with information from the ACE key.
 */

public class AceRelationMention {

	/**
	 *  the ID of the mention
	 */
	public String id;
	/**
	 *  the span of the extent of the event, with start and end positions based
	 *  on the ACE offsets (excluding XML tags).
	 */
	public Span extent;
	/**
	 *  the text of the extent of the event mention.
	 */
	public String text;
	/**
	 *  arg1:  an AceEntityMention
	 */
	public AceEntityMention arg1;
	/**
	 *  arg2:  an AceEntityMention
	 */
	public AceEntityMention arg2;
	/**
	 *  relation:  the AceRelation of which this is a mention
	 */
	public AceRelation relation;
	/**
	 *  our confidence in the presence of this relation mention
	 */
	public double confidence = 1.0;

	public AceRelationMention (String id, AceEntityMention arg1, AceEntityMention arg2,
		                         Jet.Tipster.Document doc) {
		this.id = id;
		this.arg1 = arg1;
		this.arg2 = arg2;
		int extentStart = Math.min(arg1.extent.start(), arg2.extent.start());
		int extentEnd   = Math.max(arg1.extent.end(),   arg2.extent.end());
		extent = new Span(extentStart, extentEnd);
		text = doc.text(new Span(extentStart, extentEnd+1));
	}

	/**
	 *  create an AceEntityMention from the information in the APF file.
	 *  @param mentionElement the XML element from the APF file containing
	 *                       information about this mention
	 *  @param acedoc        the AceDocument to which this relation mention
	 *                       belongs
	 */

	public AceRelationMention (Element mentionElement, AceDocument acedoc, String fileText) {
				id = mentionElement.getAttribute("ID");
				NodeList extents = mentionElement.getElementsByTagName("extent");
				Element extentElement = (Element) extents.item(0);
				extent = AceEntityMention.decodeCharseq(extentElement);
				text = fileText.substring(extent.start(), extent.end()+1);
				if (AceDocument.ace2005) {
					// get arguments (2005 format)
					NodeList arguments = mentionElement.getElementsByTagName("relation_mention_argument");
					for (int j=0; j<arguments.getLength(); j++) {
						Element argument = (Element) arguments.item(j);
						String mentionid = argument.getAttribute("REFID");
						String role = argument.getAttribute("ROLE");
						if (role.equals("Arg-1")) {
							arg1 = acedoc.findEntityMention(mentionid);
						} else if (role.equals("Arg-2")) {
							arg2 = acedoc.findEntityMention(mentionid);
						} else if (AceRelation.timeRoles.contains(role)) {
						// ignore time roles at present
						} else {
							System.err.println ("*** invalid ROLE for relation mention");
						}
					}
				} else {
					// get arguments (2004 format)
					NodeList arguments = mentionElement.getElementsByTagName("rel_mention_arg");
					for (int j=0; j<arguments.getLength(); j++) {
						Element argument = (Element) arguments.item(j);
						String mentionid = argument.getAttribute(
							AceDocument.ace2004 ? "ENTITYMENTIONID" : "MENTIONID");
						String argnum = argument.getAttribute("ARGNUM");
						if (argnum.equals("1")) {
							arg1 = acedoc.findEntityMention(mentionid);
						} else if (argnum.equals("2")) {
							arg2 = acedoc.findEntityMention(mentionid);
						} else {
							System.err.println ("*** invalid ARGNUM for relation");
						}
					}
				}
		}

		public void write (PrintWriter w) {
			w.print  ("      <relation_mention ID=\"" + id + "\"");
			// LDCLEXICALCONDITION is not scored but is required for validation prior to 2005
			if (!AceDocument.ace2005)
				w.print (" LDCLEXICALCONDITION=\"Formulaic\"");
			if (Ace.writeEventConfidence)
			w.format(" p=\"%5.3f\"", confidence);
			w.println(">");
			w.println("      <extent>");
			AceEntityMention.writeCharseq (w, extent, text);
			w.println("      </extent>");
			writeMentionArg (w, 1, arg1);
			writeMentionArg (w, 2, arg2);
			w.println("      </relation_mention>");
		}

		private void writeMentionArg (PrintWriter w, int argnum, AceEntityMention arg) {
			if (AceDocument.ace2005) {
				w.println("        <relation_mention_argument REFID=\"" + arg.id +
			          "\" ROLE=\"Arg-" + argnum + "\">");
			} else {
				String keyword = AceDocument.ace2004 ? "ENTITYMENTIONID" : "MENTIONID";
				w.println("        <rel_mention_arg " + keyword + "=\"" + arg.id +
				          "\" ARGNUM=\"" + argnum + "\">");
			}
			w.println("          <extent>");
			w.print  ("            <charseq START=\"" + arg.extent.start() +
			          "\" END=\"" + arg.extent.end() + "\">");
			w.print  (AceEntityMention.addXmlEscapes(arg.text));
			w.println(            "</charseq>");
			w.println("          </extent>");
			if (AceDocument.ace2005) {
				w.println("        </relation_mention_argument>");
			} else {
				w.println("        </rel_mention_arg>");
			}
		}

		/**
		 *  returns a String representation of the mention, consisting of
		 *  the type and subtype of the relation, and the text of the argument
		 *  mentions.
		 */

		public String toString () {
			return relation.type + ":" + relation.subtype +
			       "(" + arg1.text + ", " + arg2.text + ")";
		}

}
