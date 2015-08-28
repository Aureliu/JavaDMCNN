// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import Jet.Tipster.*;

/**
 *  the value of an AceEventMention argument:  an
 *  AceEntityMention, an AceTimexMention, an AceValueMention, or an AceEventAnchor.
 */

public abstract class AceMention implements Comparable {

	public String id;
	/**
	 *  the extent of the mention, with start and end positions based on
	 *  ACE offsets (excluding XML tags).
	 */
	public Span extent;
	/**
	 *  the extent of the mention, with start and end positions based on
	 *  Jet offsets (including all following whitespace).
	 */
	public Span jetExtent;
	/**
	 *  the text of the extent.
	 */
	public String text;
	/**
	 *  the parent (the entity, value, or timex containing this mention)
	 */
	public abstract AceEventArgumentValue getParent ();
	/**
	 *  the type (of the parent entity, value, or timex).
	 */
	public abstract String getType ();

	public Span getJetHead() {
		return jetExtent;
	}

	public String getHeadText () {
		return text;
	}

	/**
	 *  returns a positive, zero, or negative integer depending on whether the
	 *  start of the head of 'o' follows, is the same as, or precedes the head
	 *  of this AceMention.
	 */

	public int compareTo (Object o) {
		if (!(o instanceof AceMention)) throw new ClassCastException();
		int d = getJetHead().compareTo(((AceMention)o).getJetHead());
		if (d != 0)
			return d;
		else
			return typeCode(this) - typeCode((AceMention)o);
	}

	private int typeCode (AceMention o) {
		if (o instanceof AceValueMention) return 3;
		if (o instanceof AceTimexMention) return 2;
		if (o instanceof AceEntityMention) return 1;
		return 0;
	}
}
