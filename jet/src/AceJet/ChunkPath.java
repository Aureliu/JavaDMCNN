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
import Jet.Lex.Tokenizer;
import Jet.Parser.SynFun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 *  a representation of the sequence of chunks / constituents
 *  between two entity mentions.
 */

public class ChunkPath {

	final static Logger logger = LoggerFactory.getLogger(ChunkPath.class);

	/**
	 *  the heads of the chunks.
	 */
	ArrayList<String> chunks;
	Document doc;

	/**
	 *  builds the ChunkPath between two AceMentions.
	 */

	public ChunkPath (Document doc, AceMention m1, AceMention m2) {
		this.doc = doc;
		// if arg1 contains arg2
		if (m1.jetExtent.start() <= m2.jetExtent.start() &&
		    m1.jetExtent.end() >= m2.jetExtent.end()) {
			if (m1 instanceof AceEntityMention || m1 instanceof AceEventAnchor) {
			// connect head of arg1 and full extent of arg2
				chunks = patternBetweenSpans (m1.getJetHead(), m2.jetExtent);
			}
		// if arg2 contains arg1
		} else if (m2.jetExtent.start() <= m1.jetExtent.start() &&
		           m2.jetExtent.end() >= m1.jetExtent.end()) {
			if (m2 instanceof AceEntityMention || m2 instanceof AceEventAnchor) {
			// connect full extent of arg1 and head of arg2
				chunks = patternBetweenSpans (m1.jetExtent, m2.getJetHead());
			}
		} else {
			// else connect full extents of both
			chunks = patternBetweenSpans (m1.jetExtent, m2.jetExtent);
		}
	}

	/**
	 *  builds the ChunkPath from position <CODE>from</CODE> to position
	 *  <CODE>to</CODE> in Document <CODE>doc</CODE>.
	 */

	public ChunkPath (Document doc, int from, int to) {
		this.doc = doc;
		chunks = buildPattern (from, to);
	}

	/**
	 *  builds the ChunkPath between two spans.
	 */

	private ArrayList patternBetweenSpans (Span span1, Span span2) {
		ArrayList pattern = null;
		if (span1.start() < span2.start()) {
			pattern = buildPattern(span1.end(), span2.start());
		} else {
			logger.warn ("*** Unexpected span order.");
			logger.warn ("Span1 = " + doc.text(span1));
			logger.warn ("Span2 = " + doc.text(span2));
			pattern = null;
		}
		return pattern;
	}

	/**
	 *  returns a list of constituents spanning the document from
	 *  'start' to 'end'.  In choosing among constituents, prefer
	 *  the longest constituent at each starting point.  Among
	 *  constituents of the same length, prefer the one of highest
	 *  rank.
	 */

	private ArrayList<String> buildPattern (int start, int end) {
		int posn = Tokenizer.skipWS (doc, start, doc.length());
		ArrayList<String> pattern = new ArrayList<String>();
		while (posn < end) {
			Vector constits = doc.annotationsAt (posn, "constit");
			Annotation token;
			Annotation best = null;
			if (constits != null) {
				// find longest constit which does not go past 'end'
				// among constit's of same length, prefer one with highest rank
				int furthest = -1;
				int bestRank = -1;
				for (int i=0; i<constits.size(); i++) {
					Annotation constit = (Annotation) constits.get(i);
					String cat = (String) constit.get("cat");
					int constitEnd = constit.span().end();
					if ((constitEnd > furthest ||
					     (constitEnd == furthest && categoryRank(cat) > bestRank))
					    && constitEnd <= end) {
						furthest = constit.span().end();
						best = constit;
						bestRank = categoryRank(cat);
					}
				}
			}
			if (best != null) {
				String cat = (String)best.get("cat");
				String hd = SynFun.getHead(doc, best);
				if (!noiseToken(cat, hd))
					pattern.add(hd);
				posn = best.span().end();
			} else if ((token = doc.tokenAt(posn)) != null) {
				String text = doc.text(token).trim();
				pattern.add(text);
				posn = token.span().end();
			} else {
				logger.warn ("buildPattern:  no constits at position " + posn);
				String text = doc.text();
				int first = (posn < 10) ? 0 : posn - 10;
				int last = (posn + 10 > text.length()) ? text.length() : posn + 10;
				logger.warn ("               text(" + first + ":" + last + ")" +
				                    " = " + text.substring(first, last));
				return null;
			}
		}
		return pattern;
	}

	private boolean noiseToken (String cat, String head) {
		return cat == "adv" || cat == "timex" || cat == "q" ||
			 head.equals("'") || head.equals("''") || head.equals("\"") ||
			 head.equals("timex");
	}

	private static HashMap categoryRankTable = new HashMap();
	static {
		categoryRankTable.put("name", new Integer(1));
		categoryRankTable.put("timex", new Integer(1));
		categoryRankTable.put("np", new Integer(2));
		categoryRankTable.put("np-pro", new Integer(2));
		categoryRankTable.put("vgroup", new Integer(2));
		categoryRankTable.put("vgroup-inf", new Integer(2));
		categoryRankTable.put("vgroup-pass", new Integer(2));
		categoryRankTable.put("vgroup-ving", new Integer(2));
		categoryRankTable.put("vgroup-ven", new Integer(2));
		categoryRankTable.put("vp", new Integer(3));
		categoryRankTable.put("vp-inf", new Integer(3));
		categoryRankTable.put("vingo", new Integer(3));
		categoryRankTable.put("s", new Integer(4));
	}

	private static int categoryRank (String category) {
		Integer rankI = (Integer) categoryRankTable.get(category);
		if (rankI == null)
			return 0;
		else
			return rankI.intValue();
	}

	/**
	 *  returns the chunks in the ChunkPath:  an array of Strings of the heads
	 *  of the chunks (or null if no chunk sequence was found)
	 */

	public ArrayList<String> getChunks () {
		return chunks;
	}

	/**
	 *  returns the last chunk in the ChunkPath.
	 */

	public String getLastChunk () {
		if (chunks != null && chunks.size() > 0)
			return chunks.get(chunks.size()-1);
		else
			return null;
	}

	/**
	 *  returns the number of chunks in the ChunkPath, or -1 if there is
	 *  no chunk path.
	 */

	public int size() {
		if (chunks == null)
			return -1;
		else
			return chunks.size();
	}

	/**
	 *  returns true if the ChunkPath includes <CODE>chunk</CODE>.
	 */

	public boolean contains (String chunk) {
		return chunks.contains(chunk);
	}

	public String toString () {
		return LearnRelations.concat(chunks);
	}

	public void write (PrintWriter pw) {
		pw.println (toString());
	}

	public ChunkPath (String chunkString) {
		String[] chunkArray = chunkString.split(" ");
		chunks = new ArrayList(chunkArray.length);
		for (int i=0; i<chunkArray.length; i++) {
			chunks.add(chunkArray[i]);
		}
	}

	public boolean equals (ChunkPath c) {
		if (chunks == null) return false;
		if (chunks.size() != c.size()) return false;
		for (int i=0; i<chunks.size(); i++) {
			if (!chunks.get(i).equals(c.getChunks().get(i))) return false;
		}
		return true;
	}

	public int hashCode () {
		return toString().hashCode();
	}

	public int matchFromLeft (int posn, Document doc) {
		// if no path, return -1
		if (chunks == null)
			return -1;
		posn = skipNoiseTokenFromLeft (posn, doc);
		nextchunk:
		for (int ichunk=0; ichunk < chunks.size(); ichunk++) {
			String chunk = chunks.get(ichunk);
			Vector constits = doc.annotationsAt (posn, "constit");
			if (constits != null) {
				for (int i=0; i<constits.size(); i++) {
					Annotation constit = (Annotation) constits.get(i);
					String cat = (String) constit.get("cat");
					String c;
					if (cat == "adv" || cat == "timex" || cat == "q")
						c =  cat + "(" + SynFun.getHead(doc, constit) + ")";
					else
						c = SynFun.getHead(doc, constit);
					if (c.equals(chunk)) {
						posn = constit.end();
						posn = skipNoiseTokenFromLeft(posn, doc);
						continue nextchunk;
					}
				}
			}
			Annotation token;
			if ((token = doc.tokenAt(posn)) != null) {
				String text = doc.text(token).trim();
				if (text.equals(chunk)) {
					posn = token.end();
					posn = skipNoiseTokenFromLeft(posn, doc);
					continue nextchunk;
				}
			}
			return -1;
		}
		return posn;
	}

	private int skipNoiseTokenFromLeft (int posn, Document doc) {
		Vector constits = doc.annotationsAt (posn, "constit");
		if (constits != null) {
			for (int i=0; i<constits.size(); i++) {
				Annotation constit = (Annotation) constits.get(i);
				String cat = (String) constit.get("cat");
				String c;
				if (cat == "adv" || cat == "timex" || cat == "q")
					c =  cat + "(" + SynFun.getHead(doc, constit) + ")";
				else
					c = SynFun.getHead(doc, constit);
				if (RelationPattern.noiseToken(c)) {
					return (constit.end());
				}
			}
		}
		return posn;
	}

	public int matchFromRight (int posn, Document doc) {
		// if no path, return -1
		if (chunks == null)
			return -1;
		posn = skipNoiseTokenFromRight (posn, doc);
		nextchunk:
		for (int ichunk=0; ichunk < chunks.size(); ichunk++) {
			String chunk = (String) chunks.get(ichunk);
			Vector constits = doc.annotationsEndingAt (posn, "constit");
			if (constits != null) {
				for (int i=0; i<constits.size(); i++) {
					Annotation constit = (Annotation) constits.get(i);
					String cat = (String) constit.get("cat");
					String c;
					if (cat == "adv" || cat == "timex" || cat == "q")
						c =  cat + "(" + SynFun.getHead(doc, constit) + ")";
					else
						c = SynFun.getHead(doc, constit);
					if (c.equals(chunk)) {
						posn = constit.start();
						posn = skipNoiseTokenFromRight(posn, doc);
						continue nextchunk;
					}
				}
			}
			Annotation token;
			if ((token = doc.tokenEndingAt(posn)) != null) {
				String text = doc.text(token).trim();
				if (text.equals(chunk)) {
					posn = token.start();
					posn = skipNoiseTokenFromLeft(posn, doc);
					continue nextchunk;
				}
			}
			return -1;
		}
		return posn;
	}

	private int skipNoiseTokenFromRight (int posn, Document doc) {
		Vector constits = doc.annotationsEndingAt (posn, "constit");
		if (constits != null) {
			for (int i=0; i<constits.size(); i++) {
				Annotation constit = (Annotation) constits.get(i);
				String cat = (String) constit.get("cat");
				String c;
				if (cat == "adv" || cat == "timex" || cat == "q")
					c =  cat + "(" + SynFun.getHead(doc, constit) + ")";
				else
					c = SynFun.getHead(doc, constit);
				if (RelationPattern.noiseToken(c)) {
					return (constit.start());
				}
			}
		}
		return posn;
	}

}
