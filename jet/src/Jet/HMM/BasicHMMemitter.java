// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Lisp.FeatureSet;

/**
 *  a simple HMMemitter, with statistics based only on exact match to a token in
 *  the training corpus.
 */

public class BasicHMMemitter extends HMMemitter {

	private static final float VOCAB_SIZE = 40000.0f;

	int count;           // number of times this state was traversed in training
	HashMap tokenCount;  // the number of times this token was emitted in training
	HashMap tokenProbability;
	double unseenTokenProbability;

	public BasicHMMemitter () {
	}

	public void resetForTraining () {
		count = 0;
		tokenCount = new HashMap();
	}

	public void trainOnInstances (String token, String priorToken, int n) {
		count += n;
		Integer tcountI = (Integer) tokenCount.get(token);
		int tcount;
		if (tcountI == null)
			tcount = 0;
		else
			tcount = tcountI.intValue();
		tokenCount.put(token, new Integer(tcount+n));
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
		unseenTokenProbability = Math.log((double) singletonCount /
		                                  (double) count / (double) VOCAB_SIZE);
	}

	public double getProbability (String token, String priorToken, FeatureSet fs) {
		Double prob = (Double) tokenProbability.get(token);
		if (prob == null)
			return unseenTokenProbability;
		else
			return prob.doubleValue();
	}

	public void print () {
		Iterator tokenIterator = tokenProbability.entrySet().iterator();
		while (tokenIterator.hasNext()) {
			Map.Entry entry = (Map.Entry) tokenIterator.next();
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
	}

}
