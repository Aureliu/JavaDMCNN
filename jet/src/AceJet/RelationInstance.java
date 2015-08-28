// -*- tab-width: 4 -*-
package AceJet;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;

/**
 *  an instance of a (possible) relation, obtained either from
 *  the APF file or from the document.
 */

abstract class RelationInstance {
	String relationType = "";
	String relationSubtype = "";
	String syntacticLink = "";      // syntactic connective (for candidate relation)
	ArrayList linearLink;           // series of linear connectives (for candidate relation)

	abstract String getType1();
	abstract String getType2();
}

/**
 *  an instance of a (possible) relation, involving specific mentions
 *  'mention1' and 'mention2'.
 */

class RelationMention extends RelationInstance {

	String id;
	// the two mentions participating in the (possible) relation
	AceEntityMention mention1, mention2;
	boolean analyzed = false;		// true if relation has been paired with candidate
	                            // (for key relations)
	/**
	 *  our confidence in the presence of this relation mention
	 */
	public double confidence = 1.0;

	RelationMention (String t, String s) {
		relationType = t;
		relationSubtype = s;
		syntacticLink = "0";
		linearLink = new ArrayList();
		linearLink.add("0");
	}

	RelationMention (AceEntityMention m1, AceEntityMention m2) {
		mention1 = m1;
		mention2 = m2;
		syntacticLink = "0";
		linearLink = new ArrayList();
		linearLink.add("0");
	}

	void setArg (int argNum, AceEntityMention m) {
		if (argNum == 1) {
			mention1 = m;
		} else if (argNum == 2) {
			mention2 = m;
		} else {
			System.err.println ("Invalid argument " + argNum + " to setArg");
		}
		return;
	}

	void setAnalyzed () {
		analyzed = true;
	}

	void swapArgs () {
		AceEntityMention temp = mention1;
		mention1 = mention2;
		mention2 = temp;
	}

	String getType1 () {
		return mention1.type;
	}

	String getType2 () {
		return mention2.type;
	}

	public String toString() {
		return mention1.entity.type + " " + mention1.entity.subtype + " " + LearnRelations.getHead(mention1)
		       + " [ " + syntacticLink + " : " + LearnRelations.concat(linearLink) + " ] "
		       + mention2.entity.type + " " +  mention2.entity.subtype + " " + LearnRelations.getHead(mention2);
	}

	AceRelationMention toAce (Document doc, AceDocument ad) {
		AceRelationMention arm =
		       new AceRelationMention (id, ad.findEntityMention(mention1.id),
		                                   ad.findEntityMention(mention2.id), doc);
		arm.confidence = confidence;
		return arm;
	}

	AceRelationMention toAce (String rmid, Document doc, AceDocument ad) {
		AceRelationMention arm =
		       new AceRelationMention (rmid, ad.findEntityMention(mention1.id),
		                                     ad.findEntityMention(mention2.id), doc);
		arm.confidence = confidence;
		return arm;
	}

}

/**
 *  a pattern and the associated relation type and subtype, as learned from
 *  the training corpus.  In contrast to a RelationMention, this does not
 *  involve a specific pair of mentions.
 */

class RelationPattern extends RelationInstance implements Comparable  {

	String string;
	String mentionType1 = "   ", mentionSubtype1 = "", mentionHead1 = "";
	String mentionType2 = "   ", mentionSubtype2 = "", mentionHead2 = "";

  /*
   *  creates a RelationPattern from the representation in 'line'.
   *  Line has the form
   *  type1 head1 [syntactic link : linear link] type2 head2 --> relationType relationSubtype
   */

	RelationPattern (String line) {
		string = line;
		linearLink = new ArrayList();
		StringTokenizer st = new StringTokenizer(line);
		String reverseFlag = st.nextToken();
		boolean reversed = false;
		if (reverseFlag.startsWith("arg1-"))
			reversed = false;
		else if (reverseFlag.startsWith("arg2-"))
			reversed = true;
		else
			System.err.println ("Unexpected value of reverseFlag: " + reverseFlag);
		mentionType1 = st.nextToken();
		mentionSubtype1 = st.nextToken();
		if (mentionSubtype1.equals("*")) mentionSubtype1 = "";
		mentionHead1 = st.nextToken();
		if (!st.nextToken().equals("[")) {
			System.err.println ("Cannot find [ in line: " + line);
			return;
		}
		syntacticLink = st.nextToken();
		if (!st.nextToken().equals(":")) {
			System.err.println ("Cannot find : in line: " + line);
			return;
		}
		String constit;
		while (!(constit = st.nextToken()).equals("]")) {
			if (!noiseToken(constit))
				linearLink.add(constit);
		}
		mentionType2 = st.nextToken();
		mentionSubtype2 = st.nextToken();
		if (mentionSubtype2.equals("*")) mentionSubtype2 = "";
		mentionHead2 = st.nextToken();
		if (!st.nextToken().equals("-->")) {
			System.err.println ("Cannot find --> in line: " + line);
			return;
		}
		relationType = st.nextToken();
		if (st.hasMoreTokens())
			relationSubtype = st.nextToken();
		if (reversed)
			relationType += "-1";
		return;
	}
	
	/**
	 *  returns true if 'token' is an element of a linear pattern
	 *  which should be ignored in pattern matching.
	 */

	static boolean noiseToken (String token) {
		return token.startsWith("adv(") ||
		       token.startsWith("timex(") ||
		       token.startsWith("q(") ||
		       token.equals("'") ||
		       token.equals("''") ||
		       token.equals("\"");
	}

	String getType1 () {
		return mentionType1;
	}

	String getType2 () {
		return mentionType2;
	}

	/**
	 *  returns the dissimilarity of RelationInstance ri to the RelationPattern.
	 *  This distance is computed based on matches of the type, subtype, and heads
	 *  of the two arguments, and the syntactic and linear links.
	 */

	int distance (RelationInstance ri) {
		String type1, type2, subtype1, subtype2, head1, head2;
		if (ri instanceof RelationPattern) {
			RelationPattern rp = (RelationPattern) ri;
			type1 = rp.mentionType1;
			type2 = rp.mentionType2;
			subtype1 = rp.mentionSubtype1;
			subtype2 = rp.mentionSubtype2;
			head1 = rp.mentionHead1;
			head2 = rp.mentionHead2;
		} else {  // ri instance of RelationMention
			RelationMention rm = (RelationMention) ri;
			type1 = rm.mention1.entity.type;
			type2 = rm.mention2.entity.type;
			subtype1 = rm.mention1.entity.subtype;
			subtype2 = rm.mention2.entity.subtype;
			head1 = LearnRelations.getHead(rm.mention1);
			head2 = LearnRelations.getHead(rm.mention2);
		}
		if (mentionType1.length() < 3 || mentionType2.length() < 3 ||
		    type1.length() < 3 || type2.length() < 3) {
			// System.err.println ("Error in mention type length.");
			return 3;
		}
		// either types must match or heads must match
		boolean wildCard1 = mentionHead1.equals("0") || head1.equals("0");
		boolean wildCard2 = mentionHead2.equals("0") || head2.equals("0");
		boolean exactHeadMatch1 = mentionHead1.equals(head1) && !wildCard1;
		boolean exactHeadMatch2 = mentionHead2.equals(head2) && !wildCard2;
		boolean typeMatch1 =
		       (mentionType1.substring(0,3).equals(type1.substring(0,3)) ||
		        exactHeadMatch1);
		boolean subtypeMatch1 =
		       (mentionSubtype1.equals(subtype1)) || exactHeadMatch1;
		boolean typeMatch2 =
		       (mentionType2.substring(0,3).equals(type2.substring(0,3)) ||
		        exactHeadMatch2);
		boolean subtypeMatch2 =
		       (mentionSubtype2.equals(subtype2)) || exactHeadMatch2;
		// if (!(typeMatch1 && typeMatch2))
		// 	return 100;
		boolean arg1Match =
		       mentionHead1.equals(head1) || wildCard1;
		boolean arg2Match =
		       mentionHead2.equals(head2) || wildCard2;
		if (syntacticLink.equals("of") && !arg1Match)
			return 100;
		//if ((syntacticLink.equals("poss-1") || syntacticLink.equals("nameMod-1"))
		//    && !arg2Match)
		//    return 100;
		boolean syntaxMatch =
		       syntacticLink.equals(ri.syntacticLink) && !syntacticLink.equals("0");
		boolean prepMatch =
					 matchingRelations(syntacticLink, ri.syntacticLink);
		boolean linearMatch =
		       (linearLink.size() == ri.linearLink.size()) &&
		       (linearLink.size() == 0 || !linearLink.get(0).equals("0"));
		if (linearMatch) {
			for (int i=0; i<linearLink.size(); i++) {
				if (!linearLink.get(i).equals(ri.linearLink.get(i)))
					linearMatch = false;
			}
		}
		// minimal conditions:  types, one argument, and one link match
		//if (!(arg1Match | arg2Match))
		// 	return 100;
		// /*
		if (!(syntaxMatch | prepMatch | linearMatch))
			return 100;
		int dist = 0;
		if (!typeMatch1) dist += 20;
		// if (typeMatch1 & !subtypeMatch1) dist += 1;
		if (!typeMatch2) dist += 20;
		// if (typeMatch2 & !subtypeMatch2) dist += 1;
		if (!syntaxMatch) dist++;
		if (!prepMatch) dist++;
		if (!linearMatch) dist+=2;
		if (!arg1Match) dist+=8;
		if (!arg2Match) dist+=8;
		return dist;
	}

	public int compareTo (Object x) {
		RelationPattern rp = (RelationPattern) x;
		return string.compareTo(rp.string);
	}

	static String prepositionOfLink (String syntacticLink) {
		if (syntacticLink.startsWith("s-") || syntacticLink.startsWith("o-")) {
			int dash = syntacticLink.lastIndexOf('-');
			if (dash > 0)
				return syntacticLink.substring(dash+1);
		}
		return null;
	}

	static boolean matchingRelations (String syntacticLink1, String syntacticLink2) {
		String prep1 = prepositionOfLink(syntacticLink1);
		String prep2 = prepositionOfLink(syntacticLink2);
		return prep1 != null && prep1.equals(prep2);
	}
}

class RelationPatternSet {

	TreeMap patternSet, patternIndex;

	RelationPatternSet () {
		patternSet = new TreeMap();
		patternIndex = new TreeMap();
	}

	Iterator iterator () {
		return patternSet.keySet().iterator();
	}

	void load (String patternFile, int skipCount) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(patternFile));
		int count = 0;
		String line;
		while((line = reader.readLine()) != null) {
			count++;
			if (count < skipCount) continue;
			RelationPattern pattern = new RelationPattern(line);
			if (pattern.relationType.equals("")) continue;
			Integer freqI = (Integer) patternSet.get(pattern);
			int freq = (freqI==null) ? 0 : freqI.intValue();
			patternSet.put(pattern, new Integer(freq+1));
			if (!pattern.syntacticLink.equals("0")) {
				String prep = RelationPattern.prepositionOfLink (pattern.syntacticLink);
				if (prep != null) {
					indexPattern(pattern, prep);
				} else {
					indexPattern(pattern, pattern.syntacticLink);
				}
			}
			if (pattern.linearLink.size() > 0) {
				String last = (String) pattern.linearLink.get(pattern.linearLink.size()-1);
				if (!last.equals("0"))
					indexPattern(pattern, last);
			}	else {
				indexPattern(pattern, "**");
			}
		}
		System.err.println ((count - skipCount) + " patterns loaded.");
		reader.close();
	}

	private void indexPattern (RelationPattern pattern, String key) {
		HashSet set = (HashSet) patternIndex.get(key);
		if (set == null)
			set = new HashSet();
		set.add(pattern);
		patternIndex.put(key, set);
	}

	/**
	 *  confidence of match from call to 'findMatch'
	 */
	private double confidence = 1.;
	
	/**
	 *  find the best match in the RelationPatternSet to relation
	 *  instance rp, provided the dissimilarity is less than maxDistance.
	 */

	RelationPattern findMatch (RelationInstance rp, int maxDistance) {
		int bestCount = -1;
		int bestDistance = 100;
		RelationPattern bestPattern = null;
		// Iterator it = patternSet.keySet().iterator();
		HashSet candidates = null;
		if (!rp.syntacticLink.equals("0")) {
			String prep = RelationPattern.prepositionOfLink (rp.syntacticLink);
			if (prep != null) {
				candidates = (HashSet) patternIndex.get(prep);
			} else {
				candidates = (HashSet) patternIndex.get(rp.syntacticLink);
			}
		}
		if (candidates == null)
			candidates = new HashSet();
		if (rp.linearLink.size() > 0) {
			String last = (String) rp.linearLink.get(rp.linearLink.size()-1);
			if (!last.equals("0")) {
				HashSet more = (HashSet)patternIndex.get(last);
				if (more != null)
					candidates.addAll(more);
			}
		} else {
			HashSet more = (HashSet)patternIndex.get("**");
			if (more != null)
				candidates.addAll(more);
		}
		Iterator it = candidates.iterator();
		HashMap relationTypeCount = new HashMap();  // <<
		HashMap typeRelationMap = new HashMap(); // <<
		while (it.hasNext()) {
			RelationPattern pattern = (RelationPattern) it.next();
			int count = ((Integer) patternSet.get(pattern)).intValue();
			int dis = pattern.distance(rp);
			/*
			if (dis < bestDistance || (dis == bestDistance && count > bestCount)) {
				bestDistance = dis;
				bestCount = count;
				bestPattern = pattern;
			// */
			// /*
			if (dis < bestDistance) {
				relationTypeCount.clear();
				typeRelationMap.clear();
				bestDistance = dis;
			}
			if (dis == bestDistance) {
				// System.out.println ("Relation type: " + pattern.relationType);
				String typeSubType = pattern.relationType + ":" + pattern.relationSubtype;
				BuildRelationModel.incrementHashMap (relationTypeCount, typeSubType, count);
				typeRelationMap.put(typeSubType, pattern);
			// */
			}
		}
		if (bestDistance <= maxDistance) {
			/*
			return bestPattern;
			// */
			// /*
			Iterator countIt = relationTypeCount.keySet().iterator();
			String bestTypeSubType = "";
			while (countIt.hasNext()) {
				String typeSubType = (String) countIt.next();
				Integer count = (Integer) relationTypeCount.get(typeSubType);
				int ct = count.intValue();
				if (ct > bestCount) {
					bestCount = ct;
					bestTypeSubType = typeSubType;
				}
			}
			confidence = (maxDistance - bestDistance) / (double) maxDistance;
			return (RelationPattern) typeRelationMap.get(bestTypeSubType);
			// */
		} else {
			confidence = 0.;
			return null;
		}
	}
	
	/**
	 *  return confidence measure (between 0 and 1) reflecting confidence of
	 *  value returned by most recent call to findMatch.
	 */
	
	public double getMatchConfidence () {
		return confidence;
	}
}
