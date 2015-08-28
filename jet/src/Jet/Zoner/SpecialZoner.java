// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Zoner;

import Jet.Tipster.*;
import Jet.Lisp.*;
import Jet.Lex.Tokenizer;
import java.util.*;

/**
 *  methods for identifying specific zones in a document (for ACE 2005 docs)
 */

public class SpecialZoner {

	/**
	 *  marks datelines and textBreaks (blank lines and rules) within the
	 *  <B>TEXT</B> annotation of Document 'doc'.
	 */

	public static void findSpecialZones (Document doc) {
		Vector textSegments = doc.annotationsOfType ("TEXT");
		if (textSegments == null || textSegments.size() == 0)
			return;
		Annotation textAnn = (Annotation) textSegments.get(0);
		Span textSpan = textAnn.span();
		int textOffset = textSpan.start();
		String text = doc.text(textAnn);
		findDateline (doc, textOffset, text);
		findTextBreaks (doc, textOffset, text);
	}

	static String[] agency = {"(AFP)", "(AP)", "(Xinhua)"};

	/**
	 *  finds datelines in newswire texts, and marks them with a
	 *  <B>dateline</B> annotation.  This is source specific ... it
	 *  looks for a known news agency tag in the first 80 characters.
	 */

	public static void findDateline (Document doc, int textOffset, String text) {

		// look for ... (agency)
		int datelineEnd = -1;
		for (int j=0; j<agency.length; j++) {
			int i = text.indexOf(agency[j]);
			if (i > 0 && i < 80) {
				datelineEnd = text.indexOf(agency[j]) + agency[j].length();
				datelineEnd = Tokenizer.skipWS(text, datelineEnd, text.length());
				break;
			}
		}
		// if found, mark as dateline
		if (datelineEnd > 0) {
			doc.annotate ("dateline", new Span(textOffset, textOffset + datelineEnd),
			                          new FeatureSet());
		}
	}

	/**
	 *  finds text breaks marked by double blank lines or by lines consisting entirely
	 *  of "-", "~", and "_" characters, and marks the line with a <B>textBreak</B>
	 *  annotation.
	 */

	public static void findTextBreaks (Document doc, int textOffset, String text) {
		int lineStart = 0;
		while (lineStart < text.length()) {
			int lineEnd = text.indexOf('\n', lineStart);
			if (lineEnd < 0)
				lineEnd = text.length();
			String line = text.substring(lineStart, lineEnd).trim();
			if (line.length() == 0 || line.matches("^[-_~]+$"))
				doc.annotate ("textBreak",
					            new Span(lineStart + textOffset, lineEnd + textOffset),
				              new FeatureSet());
			lineStart = lineEnd + 1;
		}
	}

}
