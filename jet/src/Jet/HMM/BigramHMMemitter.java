// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Lisp.FeatureSet;

/**
 *  an HMMemitter, using statistics for exact token match (including case), for token
 *  match (ignoring case), for token bigrams (ignoring case) and for word 'shape'.
 *  Bigram information is only used for states following the first token in names
 *  (i.e., only for name-internal bigrams).
 */

public class BigramHMMemitter extends HMMemitter {

	private static final float VOCAB_SIZE = 5000.0f;

	public static boolean useBigrams = true;

	int count;           	// number of times this state was traversed in training
	HashMap tokenCount;  	// the number of times this token was emitted in training
	HashMap tokenProbability;
	HashMap priorTokenCount;

	// bigramCount and LCbigramCount are maps from prior token to maps from
	// current token to count.  'bigramCount' records actual case;
	// 'LCbigramCount' records tokens folded to lower case.
	// Probabilities are computed using LCbigramCount;  bigramCount is used only
	// to be able to print / write out counts.
	HashMap bigramCount;
	HashMap LCbigramCount;
	HashMap bigramProbability;
	HashMap LCtokenCount;  	// the number of times this token was emitted in training.
							            // regardless of case
	HashMap LCtokenProbability;
	HashMap featureCount;  	// the number of times this feature was emitted in training
	HashMap featureProbability;
	HashMap cacheCount;
	double[] inCacheProbability, notInCacheProbability;

	double discount;

	double unseenTokenProbability;

	public BigramHMMemitter () {
	}

	public void resetForTraining () {
		count = 0;
		tokenCount = new HashMap();
		priorTokenCount = new HashMap();
		bigramCount = new HashMap();
		LCbigramCount = new HashMap();
		LCtokenCount = new HashMap();
		featureCount = new HashMap();
		cacheCount = new HashMap();
	}

	public void setCacheCount (String type, int n) {
		cacheCount.put(type, new Integer(n));
	}

	public void trainOnInstances (String token, String priorToken, int n) {
		count += n;
		if (AceJet.Ace.monocase && (stateName.startsWith("b-") || stateName.startsWith("m-") ||
		                     stateName.startsWith("e-") || stateName.startsWith("i-")))
			token = capitalize(token);
		incrementHashMap (tokenCount, token, n);
		incrementHashMap (priorTokenCount, priorToken.toLowerCase(), n);
		incrementTwoLevelHashMap (bigramCount, priorToken, token, n);
		incrementTwoLevelHashMap (LCbigramCount, priorToken.toLowerCase(), token.toLowerCase(), n);
		incrementHashMap (LCtokenCount, token.toLowerCase(), n);
		incrementHashMap (featureCount, wordFeature(token), n);
		if (hmm.tagsToCache != null) {
			for (int i=0; i<hmm.tagsToCache.length; i++) {
				if (hmm.inCache(token, hmm.tagsToCache[i])) {
					incrementHashMap (cacheCount, hmm.tagsToCache[i], 1);
				}
			}
		}
	}

	private String capitalize (String token) {
		if (Character.isLowerCase(token.charAt(0)))
			return token.substring(0,1).toUpperCase() + token.substring(1);
		else
			return token;
	}

	public void computeProbabilities () {

		tokenProbability = new HashMap();
		int singletonCount = 0;
		Iterator tokenIterator = tokenCount.entrySet().iterator();
		while (tokenIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) tokenIterator.next();
			String token = (String) entry.getKey();
			int tokenCount = ((Integer) entry.getValue()).intValue();
			double probability = (double) tokenCount / (double) count;
			tokenProbability.put(token,new Double(probability));
			if (tokenCount == 1) singletonCount++;
		}

		bigramProbability = new HashMap();
		Iterator bigramIterator = LCbigramCount.entrySet().iterator();
		while (bigramIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) bigramIterator.next();
			String priorToken = (String) entry.getKey();
			int priorTokenCt = ((Integer) priorTokenCount.get(priorToken)).intValue();
			HashMap countMap2 = (HashMap) entry.getValue();
			HashMap probMap2 = new HashMap();
			Iterator it2 = countMap2.entrySet().iterator();
			while (it2.hasNext()) {
				Map.Entry entry2 = (Map.Entry) it2.next();
				String currentToken = (String) entry2.getKey();
				int bigramCt = ((Integer) entry2.getValue()).intValue();
				double probability = (double) bigramCt / (double) priorTokenCt;
				probMap2.put(currentToken,  new Double(probability));
				// if (stateName.startsWith("m-") || stateName.startsWith("e-")) {
				// 	System.out.print   ("For " + stateName);
				// 	System.out.println (" P(" + currentToken + "|" + priorToken + ")=" + probability);
				// }
			}
			bigramProbability.put(priorToken, probMap2);
		}

		LCtokenProbability = new HashMap();
		Iterator LCtokenIterator = LCtokenCount.entrySet().iterator();
		while (LCtokenIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) LCtokenIterator.next();
			String LCtoken = (String) entry.getKey();
			int LCtokenCount = ((Integer) entry.getValue()).intValue();
			double probability = (double) LCtokenCount / (double) count;
			LCtokenProbability.put(LCtoken,new Double(probability));
		}

		featureProbability = new HashMap();
		Iterator featureIterator = featureCount.entrySet().iterator();
		while (featureIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) featureIterator.next();
			String feature = (String) entry.getKey();
			int featureCount = ((Integer) entry.getValue()).intValue();
			double probability = Math.log((double) featureCount / (double) count);
			featureProbability.put(feature,new Double(probability));
		}

		Integer c1 = (Integer) featureCount.get("initCap");
		Integer c2 = (Integer) featureCount.get("lowerCase");
		int c3 = (c1==null ? 0 : c1.intValue()) + (c2==null ? 0 : c2.intValue());
		if (c3 != 0) {
			double pfc = Math.log((double) c3 / (double) count);
			featureProbability.put("forcedCap", new Double(pfc));
		}

		unseenTokenProbability = Math.log((double) singletonCount / count /
		                                  VOCAB_SIZE);

		if (hmm.tagsToCache != null) {
			inCacheProbability = new double[hmm.tagsToCache.length];
			notInCacheProbability = new double[hmm.tagsToCache.length];
			if (HMM.probReport)
				System.out.println ("For state " + stateName);
			for (int i=0; i<hmm.tagsToCache.length; i++) {
				Integer cacheCt = (Integer) cacheCount.get(hmm.tagsToCache[i]);
				int cc = cacheCt == null ? 0 : cacheCt.intValue();
				if (HMM.probReport) {
					System.out.println ("Cache count[" + hmm.tagsToCache[i] + "]=" + cc);
					System.out.println ("inCacheProbability[" + hmm.tagsToCache[i] + "]=" +
						((double)cc / (double) count));
				}
				inCacheProbability[i] = Math.log((double)cc / (double) count);
				notInCacheProbability[i] = Math.log(1. - (double)cc / (double) count);
			}
		}
	}

	public double getProbability (String token, String priorToken, FeatureSet fs) {
		double unseenFeatureProbability = -8.0;
		Double uncondProb;
		double prob;
		boolean forcedCap = fs.get("case") == "forcedCap";

		double condProb = 0.;
		double outcomesOfPriorToken = 0.;
		priorToken = priorToken.toLowerCase();
		HashMap m2 = (HashMap) bigramProbability.get(priorToken);
		if (m2 != null) {
			Double p = (Double) m2.get(token.toLowerCase());
			if (p != null) {
				condProb = p.doubleValue();
			}
			outcomesOfPriorToken = m2.size();
		}
		double lambda = 0;
		if (useBigrams && priorTokenCount.get(priorToken) != null) {
			int cY = ((Integer) priorTokenCount.get(priorToken)).intValue();
			if ((stateName.startsWith("m-") || stateName.startsWith("e-")) & condProb > 0.)
				lambda = 1. / (1. + outcomesOfPriorToken / cY);
		}
		if (forcedCap || AceJet.Ace.monocase) {
			uncondProb = (Double) LCtokenProbability.get(token.toLowerCase());
		} else {
			uncondProb = (Double) tokenProbability.get(token);
		}
		if (uncondProb != null) {
			prob = lambda * condProb + (1 - lambda) * uncondProb.doubleValue();
			// if (lambda > 0) {
			// 	System.out.println ("For " + priorToken + " " + token);
			// 	System.out.println ("  uncond= " + uncondProb.doubleValue() + " cond= " + condProb
			// 	                    + " prob= " + prob);
			// }
			prob = Math.log(prob);
		} else {
			String tokenForm = wordFeature(token);
			if ((forcedCap && tokenForm == "initCap") || AceJet.Ace.monocase)
				tokenForm = "forcedCap";
			Double fprob = (Double) featureProbability.get(tokenForm);
			if (fprob != null)
				prob = unseenTokenProbability + fprob.doubleValue();
			else
				prob = unseenTokenProbability + unseenFeatureProbability;
		}

		if (hmm.tagsToCache != null) {
			for (int i=0; i<hmm.tagsToCache.length; i++) {
				String tag = hmm.tagsToCache[i];
				boolean inCache = hmm.inCache(token, tag);
				if (inCache) {
					// System.out.println ("Found " + token + " in cache as " + tag);
					prob += inCacheProbability[i];
				} else
					prob += notInCacheProbability[i];
			}
		}

		return prob;
	}

	public void print () {
		Iterator bigramIterator = bigramCount.entrySet().iterator();
		while (bigramIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) bigramIterator.next();
			String priorToken = (String) entry.getKey();
			HashMap map2 = (HashMap) entry.getValue();
			Iterator it2 = map2.entrySet().iterator();
			while (it2.hasNext()) {
				Map.Entry entry2 = (Map.Entry) it2.next();
				String currentToken = (String) entry2.getKey();
				int bigramCt = ((Integer) entry2.getValue()).intValue();
				System.out.println ("EMIT " + priorToken + " " + currentToken + " " + bigramCt);
			}
		}
	}

	public void store (PrintWriter stream) {
		Iterator bigramIterator = bigramCount.entrySet().iterator();
		while (bigramIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) bigramIterator.next();
			String priorToken = (String) entry.getKey();
			HashMap map2 = (HashMap) entry.getValue();
			Iterator it2 = map2.entrySet().iterator();
			while (it2.hasNext()) {
				Map.Entry entry2 = (Map.Entry) it2.next();
				String currentToken = (String) entry2.getKey();
				int bigramCt = ((Integer) entry2.getValue()).intValue();
				stream.println ("EMIT " + priorToken + " " + currentToken + " " + bigramCt);
			}
		}
		Iterator cacheIterator = cacheCount.entrySet().iterator();
		while (cacheIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) cacheIterator.next();
			String type = (String) entry.getKey();
			int count = ((Integer) entry.getValue()).intValue();
			stream.println ("PREVTAGGED " + type + " " + count);
		}
	}

	static String wordFeature (String word) {
		int len = word.length();
		boolean allDigits = true;
		boolean allCaps = true;
		boolean initCap = true;
		boolean allLower = true;
		boolean hyphenated = true;
		for (int i=0; i<len; i++) {
			char c = word.charAt(i);
			if (!Character.isDigit(c)) allDigits = false;
			if (!Character.isUpperCase(c)) allCaps = false;
			if (!Character.isLowerCase(c)) allLower = false;
			if (!(Character.isLetter(c) || c == '-')) hyphenated = false;
			if ((i == 0 && !Character.isUpperCase(c)) ||
			    (i > 0  && !Character.isLowerCase(c))) initCap = false;
		}
		if (allDigits) {
			if (len == 2) {
				return "twoDigitNum";
			} else if (len == 4) {
				return "fourDigitNum";
			} else {
				return "otherNum";
			}
		} else if (allCaps) {
			return "allCaps";
		} else if (initCap) {
			return "initCap";
		} else if (allLower) {
			return "lowerCase";
		// for POS
		} else if (hyphenated) {
			return "hyphenated";
		} else return "other";
	}

	private static void incrementHashMap (HashMap map, String key, int n) {
		int count;
		Integer countI = (Integer) map.get(key);
		if (countI == null)
			count = 0;
		else
			count = countI.intValue();
		map.put(key, new Integer(count+n));
	}

	private static void incrementTwoLevelHashMap (HashMap map, String key1, String key2, int n) {
		HashMap map2 = (HashMap) map.get(key1);
		if (map2 == null) {
			map2 = new HashMap();
			map.put(key1, map2);
		}
		incrementHashMap (map2, key2, n);
	}
}
