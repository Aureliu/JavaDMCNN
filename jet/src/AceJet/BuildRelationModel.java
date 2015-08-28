// -*- tab-width: 4 -*-
package AceJet;

/**
 *  Takes a set of pattern files,
 *    builds a statistical model from the files
 *    evaluates the effectiveness of the model at predicting types
 */

import java.util.*;
import java.io.*;

class BuildRelationModel {

	static RelationPattern relPat;
	static StringBuffer features = new StringBuffer();

	static final String ACEdir =
	    "C:/Documents and Settings/Ralph Grishman/My Documents/ACE/";
	static final String rootDir = ACEdir + "relations/";
	static final String testPatternFile = rootDir + "patterns.log";
	static final String patternFile = rootDir + "patterns.log";
	static final String handPatternFile = ACEdir + "lisp/" + "patterns.log";
	static final String generalPatternFile = ACEdir + "lisp/" + "generalPatterns.log";

	static RelationPatternSet adam, eve, general;

	static final int testCorpusSize = 2000;  // 400;

	public static void main (String[] args) throws IOException {

		String prep = RelationPattern.prepositionOfLink("s-verb-in");
		System.out.println (prep);
		boolean prepMatch = RelationPattern.matchingRelations("s-verb-in", "s-werb-in");
		System.out.println (prepMatch);
		/*
		adam = new RelationPatternSet();
		adam.load(handPatternFile, 0 );
		eve = new RelationPatternSet();
		eve.load(patternFile, testCorpusSize);
		general = new RelationPatternSet();
		general.load(generalPatternFile, 0);
		buildProbModel (eve);
		predict();
		*/
	}

	static final String[] typeSubtype =
		{"total",
		 "no relation",
		 "PHYS      Located",
		 "PHYS      Near",
		 "PHYS      Part-Whole",
		 "PER-SOC   Business",
		 "PER-SOC   Family",
		 "PER-SOC   Other",
		 "EMP-ORG   Employ-Executive",
		 "EMP-ORG   Employ-Staff",
		 "EMP-ORG   Employ-Undetermined",
		 "EMP-ORG   Member-of-Group",
		 "EMP-ORG   Subsidiary",
		 "EMP-ORG   Partner",
		 "EMP-ORG   Other",
		 "ART       User-or-Owner",
		 "ART       Inventor-Manufacturer",
		 "ART       Other",
		 "OTHER-AFF Ethnic",
		 "OTHER-AFF Ideology",
		 "OTHER-AFF Other",
		 "GPE-AFF   Citizen-or-Resident",
		 "GPE-AFF   Based-In",
		 "GPE-AFF   Other",
		 "DISC      "};

	private static final int TYPELENGTH = 9;
	private static final int TOTAL = 0;
	static final int NO_RELATION = 1;
	private static final int N_SUBTYPES = typeSubtype.length;

	private static int[] subtypeCount = new int[N_SUBTYPES];
	private static int relationCount = 0;
	private static int MAX_SIZE = 25;
	private static int[] relationLengthCount = new int[MAX_SIZE];
	private static int[] lengthCount = new int[MAX_SIZE];
	private static HashMap[] nonFinalWordCount = new HashMap[N_SUBTYPES];
	private static HashMap[] finalWordCount = new HashMap[N_SUBTYPES];

	static void buildProbModel (RelationPatternSet rps) {
		for (int i=0; i<N_SUBTYPES; i++) {
			nonFinalWordCount[i] = new HashMap();
			finalWordCount[i] = new HashMap();
		}
		Iterator it = rps.iterator();
		while (it.hasNext()) {
			RelationPattern pattern = (RelationPattern) it.next();
			String type = pattern.relationType;
			String subType = pattern.relationSubtype;
			int itype;
			if (type.equals("0")) {
				itype = NO_RELATION;
			} else {
				itype = typeSubtypeToIndex(type, subType);
				if (itype < 0) {
					System.err.println ("*** unknown type/subtype" + type + ":" + subType);
					continue;
				}
			}
			ArrayList lilink = pattern.linearLink;
			int size = lilink.size();
			// if (size >= MAX_SIZE)
			//  	System.err.println ("*** Huge pattern " + lilink);
			if (size > 0 && size < MAX_SIZE && !lilink.get(0).equals("0")) {
				if (!type.equals("0")) {
					relationCount++;
					relationLengthCount[size]++;
				}
				lengthCount[size]++;
				subtypeCount[TOTAL]++;
				subtypeCount[itype]++;
				for (int i=0; i<size-1; i++) {
					incrementHashMap (nonFinalWordCount[TOTAL], (String) lilink.get(i), 1);
					incrementHashMap (nonFinalWordCount[itype], (String) lilink.get(i), 1);
				}
				incrementHashMap (finalWordCount[TOTAL], (String) lilink.get(size-1), 1);
				incrementHashMap (finalWordCount[itype], (String) lilink.get(size-1), 1);
			}
		}
	}

	static int mostLikelySubtype (RelationInstance relpat) {
		String argType1 = relpat.getType1();
		String argType2 = relpat.getType2();
		int bestType = -1;
		double bestProb = -1;
		for (int iType=1; iType<N_SUBTYPES; iType++) {
			double prob = subtypeProb (iType, relpat.linearLink);
			if (prob > bestProb) {
				bestType = iType;
				bestProb = prob;
			}
		}
		if (bestType > 0)
			System.out.println (">>> Best stat. type = " + typeSubtype[bestType]);
		return bestType;
	}

	static final double NO_RELATION_BIAS = 0.2;
	static final double VOCAB_SIZE = 4000.;
	static final boolean trace = false;
	private static double BETA = 0.1;

	private static double subtypeProb (int iType, ArrayList linearLink) {
		int size = linearLink.size();
		if (size == 0 || size >= MAX_SIZE || linearLink.get(0).equals("0"))
			return -1;
		double prob = 1.0;
		String word;
		for (int i=0; i<size-1; i++) {
			prob *= wordSubtypeProb (iType, (String) linearLink.get(i), false);
		}
		prob *= wordSubtypeProb (iType, (String) linearLink.get(size-1), true);
		if (iType == NO_RELATION) {
			// prob(no relation | length)
			double f1 = 1. - (double) relationLengthCount[size] / lengthCount[size];
			prob *= f1 * NO_RELATION_BIAS;
			if (trace) System.out.println ("No relation:  f1 = " + f1 + ", prob = " + prob);
		} else {
			// prob(relation itype | some relation)
			double f1 = (double) subtypeCount[iType] / relationCount;
			// prob(some relation | length)
			double f2 = (double) relationLengthCount[size] / lengthCount[size];
			prob *= f1 * f2;
			if (trace) System.out.println ("Relation " + typeSubtype[iType] + ":  f1 = " + f1 +
					", f2 = " + f2 + ", prob = " + prob);
		}
		return prob;
	}

	private static double wordSubtypeProb (int iType, String word, boolean last) {
		Integer count;
		int ct;
		double prob;
		if (last)
			count = (Integer) finalWordCount[iType].get(word);
		else
			count = (Integer) nonFinalWordCount[iType].get(word);
		ct = count==null ? 0 : count.intValue();
		if (ct > 0)
			prob = (double) ct / (double) subtypeCount[iType];
		else {
			if (last)
				count = (Integer) finalWordCount[TOTAL].get(word);
			else
				count = (Integer) nonFinalWordCount[TOTAL].get(word);
			ct = count==null ? 0 : count.intValue();
			if (ct > 0)
				prob = BETA * (double) ct / (double) subtypeCount[TOTAL];
			else
				prob = 1. / VOCAB_SIZE;
		}
		if (trace)
			System.out.println ("P(" + word + "|" + typeSubtype[iType] + ")=" + prob);
		return prob;
	}

	/**
	 *  converts a type : subType to an integer.
	 */

	private static int typeSubtypeToIndex (String type, String subType) {
		if (type.equals("0"))
			return NO_RELATION;
		for (int i=2; i<N_SUBTYPES; i++) {
			String tSt = typeSubtype[i];
			String t = tSt.substring(0,9).trim();
			String sT = tSt.substring(10);
			if (type.equals(t) && subType.equals(sT))
				return i;
		}
		return -1;
	}

	static void incrementHashMap (HashMap map, String key, int n) {
		int count;
		Integer countI = (Integer) map.get(key);
		if (countI == null)
			count = 0;
		else
			count = countI.intValue();
		map.put(key, new Integer(count+n));
	}

	private static void predict () throws IOException {
		String line;
		int count = 0;
		int correct = 0;
		int spurious = 0;
		int missing = 0;
		int incorrect = 0;
		BufferedReader reader = new BufferedReader(new FileReader(testPatternFile));
		while((line = reader.readLine()) != null) {
			count++;
			if (count > testCorpusSize) break;
			relPat = new RelationPattern (line);
			// look first for match in corpus file
			RelationPattern match1 = adam.findMatch(relPat, 5);
			RelationPattern match2 = eve.findMatch(relPat, 21);
			RelationPattern match3 = general.findMatch(relPat, 5);
			String predictedType;
			if (match1 != null)
				predictedType = match1.relationType;
			else if (match2 != null)
				predictedType = match2.relationType;
			else if (match3 != null)
				predictedType = match3.relationType;
			else {
				// predictedType  = m.getBestOutcome(m.eval(buildPredictFeatures()));
				int i = mostLikelySubtype(relPat);
				if (i < 0 || i == NO_RELATION)
					predictedType = "0";
				else
					predictedType = typeSubtype[i].substring(0,4).trim();
			}
			// if(!(relPat.relationType.equals("0") && predictedType.equals("0"))) {
			if (!relPat.relationType.equals(predictedType)) {
			// if (true) {
				System.out.println (line);
				System.out.println
				    ("Correct type: " + relPat.relationType + " Predicted type:  " + predictedType);
				if (match1 != null)
				    System.out.println ("Best Adam pattern = " + match1.string);
				else if (match2 != null)
				    System.out.println ("Best corpus pattern = " + match2.string);
				else if (match3 != null)
					System.out.println ("Best gen'l pattern = " + match3.string);
				else
					System.out.println ("No pattern matched.");
			}
			if (relPat.relationType.equals("0")) {
				if (!predictedType.equals("0"))
					spurious++;
			} else {
				if (relPat.relationType.equals(predictedType))
					correct++;
				else if (predictedType.equals("0"))
					missing++;
				else
					incorrect++;
			}
		}
		System.out.println (correct + " correct predictions");
		System.out.println (spurious + " spurious");
		System.out.println (missing + " missing");
		System.out.println (incorrect + " incorrect");
		System.out.println ("Recall = " + ((float) correct) / (correct + incorrect + missing));
		System.out.println ("Precision = " + ((float) correct) / (correct + incorrect + spurious));
		System.out.println ("Value = " + (correct - spurious - missing - incorrect));
	}

}
