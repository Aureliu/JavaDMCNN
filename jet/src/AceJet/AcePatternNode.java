// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import Jet.Tipster.*;
import java.util.*;
import java.io.*;

/**
 *  a component of an EventPattern which matches an argument of the event,
 *  an AceMention.
 */

public class AcePatternNode {

	String type, subtype, head;

	public AcePatternNode (AceMention mention) {
		type = mention.getType();
		if (mention instanceof AceEntityMention) {
			subtype = ((AceEntityMention)mention).entity.subtype;
			head = ((AceEntityMention)mention).headText.replace('\n', ' ');
		} else {
			subtype = "";
			head = mention.text.replace('\n', ' ');
		}
	}

	/**
	 *  create an AcePatternNode from a line produced by the 'write' method.
	 */

	public AcePatternNode (String s) {
		if (s == null) {
			System.err.println ("AcePatternNode: null constructor argument");
			return;
		}
		String[] fields = s.split(" \\| ");
		if (fields.length != 3) {
			System.err.println ("AcePatternNode: invalid constructor argument: " + s);
			return;
		}
		type = fields[0];
		subtype = fields[1];
		head = fields[2];
	}

	public boolean equals (Object o) {
		if (!(o instanceof AcePatternNode))
			return false;
		AcePatternNode p = (AcePatternNode) o;
		return type.equals(p.type) &&
		       subtype.equals(p.subtype) &&
		       head.equals(p.head);
	}

	public int hashCode () {
		return (type + subtype + head).hashCode();
	}

	public String toString () {
		return type + "." + subtype;
	}

	public void write (PrintWriter pw) {
		pw.println (type + " | " + subtype + " | " + head);
	}

	static final int MATCH_SCORE_ANY_MENTION   = 10000;
	static final int MATCH_SCORE_TYPE_MATCH    = 10100;
	static final int MATCH_SCORE_SUBTYPE_MATCH = 10200;
	static final int MATCH_SCORE_WORD_MATCH    = 10300;

	/**
	 *  returns a score reflecting the degree of match (similarity) between
	 *  this node and mention 'm'.
	 */

	public int match (AceMention m) {
		int score = MATCH_SCORE_ANY_MENTION;
		if (type.equals(m.getType())) {
			score = MATCH_SCORE_TYPE_MATCH;
			// higher match scores only for entity mentions
			if (m instanceof AceEntityMention) {
				AceEntityMention aem = (AceEntityMention) m;
				if (subtype.equals(aem.entity.subtype)) {
					score = MATCH_SCORE_SUBTYPE_MATCH;
					if (head.equals(aem.headText)) {
						score = MATCH_SCORE_WORD_MATCH;
					}
				}
			}
		}
		return score;
	}

	/**
	 *  looks for an entity mention matching the AcePatternNode starting at position
	 *  <CODE>posn</CODE> in Document <CODE>doc</CODE>.  Returns the entity mention
	 *  if one is found (in the AceDocument <CODE>aceDoc</CODE>), otherwise <B>null</B>.
	 */

	public AceMention matchFromLeft (int posn, Document doc, AceDocument aceDoc) {
		ArrayList mentions = aceDoc.getAllMentions();
		for (int i=0; i<mentions.size(); i++) {
			AceMention m = (AceMention) mentions.get(i);
			if (m.jetExtent.start() == posn && type.equals(m.getType())) {
				return m;
			}
		}
		return null;
	}

	/**
	 *  looks for an entity mention matching the AcePatternNode <I>ending</I> at position
	 *  <CODE>posn</CODE> in Document <CODE>doc</CODE>.  Returns the entity mention
	 *  if one is found (in the AceDocument <CODE>aceDoc</CODE>), otherwise <B>null</B>.
	 */

	public AceMention matchFromRight (int posn, Document doc, AceDocument aceDoc) {
		ArrayList mentions = aceDoc.getAllMentions();
		for (int i=0; i<mentions.size(); i++) {
			AceMention m = (AceMention) mentions.get(i);
			if (m.jetExtent.end() == posn && type.equals(m.getType())) {
				return m;
			}
		}
		return null;
	}

	private AceMention lastMatchedMention = null;

	/**
	 *  looks for an entity mention matching the AcePatternNode whose <I>head</I>
	 *  begins at position <CODE>posn</CODE> in Document <CODE>doc</CODE>.
	 *  Returns the entity mention if one is found (in the AceDocument
	 *  <CODE>aceDoc</CODE>), otherwise <B>null</B>.
	 */

	public int matchOnHead (int posn, Document doc, AceDocument aceDoc) {
		ArrayList mentions = aceDoc.getAllMentions();
		for (int i=0; i<mentions.size(); i++) {
			AceMention m = (AceMention) mentions.get(i);
			int start = (m instanceof AceEntityMention) ?
			             ((AceEntityMention)m).jetHead.start() : m.extent.start();
			if (start == posn /* && type.equals(m.getType()) */ ) {
				lastMatchedMention = m;
				return match(m);
			}
		}
		lastMatchedMention = null;
		return -1;
	}

	public AceMention getMatchedMention () {
		return lastMatchedMention;
	}

}
