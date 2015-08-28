// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Lisp.FeatureSet;

/**
 *  an HMMemitter, using statistics for exact token match (including case), for token
 *  match (ignoring case), for suffix, and for word 'shape'.  Warning:  this class
 *  uses static variables for counts associated with the entire HMM, so it should
 *  only be used for a single HMM.
 */

public class SuffixHMMemitter extends HMMemitter {

	private static final float VOCAB_SIZE = 5000.0f;
	private static final int suffixLength = 3;
	private static final double threshold = 10.;
	static int allStateCount = 0;
	static HashMap suffixCount = new HashMap();

	int count;           	// number of times this state was traversed in training
	HashMap tokenCount;  	// the number of times this token was emitted in training
	HashMap tokenProbability;
	HashMap LCtokenCount;  	// the number of times this token was emitted in training.
							// regardless of case
	HashMap LCtokenProbability;
	HashMap featureCount;  	// the number of times this feature was emitted in training
	HashMap featureProbability;
	HashMap cacheCount;
	HashMap stateSuffixCount, stateSuffixTokens, suffixProbability;

	double[] inCacheProbability, notInCacheProbability;

	double lambda;			// weight of tokenProb vs. weight of featureProb

	double unseenTokenProbability;

	public SuffixHMMemitter () {
	}

	public void resetForTraining () {
		count = 0;
		// allStateCount = 0;
		tokenCount = new HashMap();
		LCtokenCount = new HashMap();
		featureCount = new HashMap();
		cacheCount = new HashMap();
		// suffixCount = new HashMap();
		stateSuffixCount = new HashMap();
		stateSuffixTokens = new HashMap();
	}

	public void setCacheCount (String type, int n) {
		cacheCount.put(type, new Integer(n));
	}

	public void trainOnInstances (String token, String priorToken, int n) {
		count += n;
		allStateCount += n;
		incrementHashMap (tokenCount, token, n);
		incrementHashMap (LCtokenCount, token.toLowerCase(), n);
		incrementHashMap (featureCount, wordFeature(token), n);
		if (hmm.tagsToCache != null) {
			for (int i=0; i<hmm.tagsToCache.length; i++) {
				if (hmm.inCache(token, hmm.tagsToCache[i])) {
					incrementHashMap (cacheCount, hmm.tagsToCache[i], 1);
				}
			}
		}
		if (token.length() > suffixLength) {
			String suffix = token.substring(token.length()-suffixLength);
			incrementHashMap (suffixCount, suffix, n);
			incrementHashMap (stateSuffixCount, suffix, n);
			HashSet h = (HashSet) stateSuffixTokens.get(suffix);
			if (h == null) h = new HashSet();
			h.add(token);
			stateSuffixTokens.put(suffix, h);
		}
	}

	public void computeProbabilities () {
		tokenProbability = new HashMap();
		int singletonCount = 0;
		Iterator tokenIterator = tokenCount.entrySet().iterator();
		while (tokenIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) tokenIterator.next();
			String token = (String) entry.getKey();
			int tokenCount = ((Integer) entry.getValue()).intValue();
			double probability = Math.log((double) tokenCount / (double) count);
			tokenProbability.put(token,new Double(probability));
			if (tokenCount == 1) singletonCount++;
		}
		LCtokenProbability = new HashMap();
		Iterator LCtokenIterator = LCtokenCount.entrySet().iterator();
		while (LCtokenIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) LCtokenIterator.next();
			String LCtoken = (String) entry.getKey();
			int LCtokenCount = ((Integer) entry.getValue()).intValue();
			double probability = Math.log((double) LCtokenCount / (double) count);
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
		suffixProbability = new HashMap();
		Iterator suffixIterator = suffixCount.entrySet().iterator();
		while (suffixIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) suffixIterator.next();
			String suffix = (String) entry.getKey();
			int suffixCt = ((Integer) entry.getValue()).intValue();
			HashSet h = (HashSet) stateSuffixTokens.get(suffix);
			int stateSuffixTypes = h==null ? 0 : h.size();
			Integer ssc = (Integer) stateSuffixCount.get(suffix);
			int stateSuffixCt = ssc==null ? 0 : ssc.intValue();
			double expected = suffixCt * count / allStateCount;
			if (stateSuffixTypes > 5) { // || expected > threshold) {
				double pfactor = Math.log((stateSuffixCt + 2)/ (expected + 2));
				if (pfactor > 0.) {
					if (HMM.probReport)
						System.out.println ("For state " + stateName +
							" pfactor(" + suffix + ") = " + pfactor +  "tokens=" + h);
					suffixProbability.put(suffix, new Double(pfactor));
				}
			}
		}

		lambda = 1. / (1. + (((double)tokenCount.size()) / (double)count));
		// System.out.println ("tokenCount.size() = " + tokenCount.size());
		// System.out.println ("count = " + count);
		// System.out.println ("lambda = " + lambda);
		unseenTokenProbability = Math.log((double) singletonCount /
		                                  (double) count / (double) VOCAB_SIZE);

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
		Double tprob;
		double prob;
		boolean forcedCap = fs.get("case") == "forcedCap";

		if (forcedCap) {
			tprob = (Double) LCtokenProbability.get(token.toLowerCase());
		} else {
			tprob = (Double) tokenProbability.get(token);
		}
		if (tprob != null)
			prob = tprob.doubleValue();
		else {
			String tokenForm = wordFeature(token);
			if (forcedCap && tokenForm == "initCap")
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

		if (token.length() > suffixLength) {
			String suffix = token.substring(token.length()-suffixLength);
			Double sp = (Double) suffixProbability.get(suffix);
			if (sp != null) prob += sp.doubleValue();
		}

		return prob;
		/*
		Double tLogProb = (Double) tokenProbability.get(token);
		double tProb = (tLogProb == null) ? 0. : Math.exp(tLogProb.doubleValue());
		Double fLogProb = (Double) featureProbability.get(wordFeature(token));
		double fProb = Math.exp((fLogProb == null) ? unseenFeatureProbability :
													 fLogProb.doubleValue());
		double prob = lambda * tProb + (1. - lambda) * fProb;
		return Math.log(prob);
		*/
	}

	public void print () {
		Iterator featureIterator = featureProbability.entrySet().iterator();
		while (featureIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) featureIterator.next();
			String token = (String) entry.getKey();
			double probability = ((Double) entry.getValue()).doubleValue();
			System.out.println ("EMIT " + token + " " + probability);
		}
	}

	public void store (PrintWriter stream) {
		Iterator tokenIterator = tokenCount.entrySet().iterator();
		while (tokenIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) tokenIterator.next();
			String token = (String) entry.getKey();
			int count = ((Integer) entry.getValue()).intValue();
			stream.println ("EMIT " + token + " " + count);
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
}
