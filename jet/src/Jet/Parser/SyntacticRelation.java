// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil

package Jet.Parser;

import java.util.*;

/**
 *  a syntactic relation between two elements of a sentence, such as a
 *  <I>subject</I> or <I>object</I> relation.  The relation is viewed
 *  as a dependency relation -- that is, as a relation or directed arc
 *  between words (between the heads of the constituents involved).  For
 *  example, in the sentence "Cats chase mice." there will be an
 *  <I>subject</I> relation whose source is "chase" and whose target
 *  is "Cats".
 */

public class SyntacticRelation {

	/**
	 *  the word (head) at the source of the relation.
	 */
	public String sourceWord;
	/**
	 *  the part of speech of the source word
	 */
	public String sourcePos;
	/**
	 *  the position (offset within the document text of the first character)
	 *  of the source word.
	 */
	public int sourcePosn;
	/**
	 *  the type of the relation.
	 */
	public String type;
	/**
	 *  the word (head) at the target of the relation.
	 */
	public String targetWord;
	/**
	 *  the part of speech of the target word
	 */
	public String targetPos;
	/**
	 *  the position (offset within the document text of the first character)
	 *  of the target word.
	 */
	public int targetPosn;
	/**
	 *  true for relations (links) which are considered transparent by GLARF.
	 */
	public boolean transparent;

	public String sourceWordSense;
	/**
	 *  constructs a SyntacticRelation with the specified source, type, and
	 *  target.
	 */
	public SyntacticRelation (int sourcePosn, String sourceWord, String sourcePos, String type,
	                          int targetPosn, String targetWord, String targetPos) {
		this.sourcePosn = sourcePosn;
		this.sourceWord = sourceWord;
		this.sourcePos = sourcePos;
		this.type = type;
		this.targetPosn = targetPosn;
		this.targetWord = targetWord;
		this.targetPos = targetPos;
		transparent = false;
		sourceWordSense = "";
	}

	/**
	 *  constructs a SyntacticRelation with the specified source, type, and
	 *  target.
	 */
	public SyntacticRelation (int sourcePosn, String sourceWord, String type,
	                          int targetPosn, String targetWord) {
		this (sourcePosn, sourceWord, "?", type, targetPosn, targetWord, "?");
	}

	/**
	 *  constructs a SyntacticRelation from a String of the form     <br>
	 *  type | sourceWord | sourcePosn | targetWord | targetPosn
	 */
	public SyntacticRelation (String s) {
		String fields[] = s.split(" \\| ");
		if (fields.length != 5) {
			System.out.println ("SyntacticRelation: invalid constructor argument: " + s);
			return;
		}
		try {
			type = fields[0];
			sourceWord = fields[1];
			sourcePosn = Integer.parseInt(fields[2]);
			targetWord = fields[3];
			targetPosn = Integer.parseInt(fields[4]);
		} catch (NumberFormatException e) {
			System.out.println ("SyntacticRelation: invalid numeric in constructor argument: " + s);
		}
	}

	public boolean equals (Object o) {
		if (!(o instanceof SyntacticRelation))
			return false;
		SyntacticRelation p = (SyntacticRelation) o;
		return sourcePosn == p.sourcePosn &&
		       sourceWord.equals(p.sourceWord) &&
		       type.equals(p.type) &&
		       targetPosn == p.targetPosn &&
		       targetWord.equals(p.targetWord);
	}

	public int hashCode () {
		return (sourcePosn + sourceWord + type + targetPosn + targetWord).hashCode();
	}

	public void setTransparent (boolean transFlag) {
		transparent = transFlag;
	}

	public String toString () {
		return sourceWord + " (" + sourcePosn + ") " +
			     type + " " +
			     targetWord + " (" + targetPosn + ") ";
	}

}
