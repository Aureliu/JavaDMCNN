// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;

/**
 *  an arc (from one state to another) in a Hidden Markov Model.
 */

public class HMMarc {
	String targetStateName;
	int target;
	int count;                // the number of times this arc was traversed in training
	double probability;

	/**
	 *  create a new arc to the state named 'targetStateName'.  'count' is the number
	 *  of times this arc was traversed in the training corpus.
	 */

	HMMarc (String targetStateName, int count) {
		this.targetStateName = targetStateName;
		this.count = count;
	}

	void resolveTarget(HashMap statesByName) {
		if (statesByName.containsKey(targetStateName)) {
			target = ((Integer) statesByName.get(targetStateName)).intValue();
		} else {
			System.out.println ("Undefined state " + targetStateName + " in HMM");
		}
	}

	/**
	 *  initialize arc prior to training.
	 */

	void resetForTraining () {
		count = 0;
	}

	/**
	 *  compute the probability for arc transition from the counts acquired
	 *  during training.
	 */

	void computeProbabilities (int stateCount) {
		if (stateCount > 0)
			probability = Math.log(((double) count + 0.01) / (double) stateCount);
		else
			probability = HMM.UNLIKELY;
		if (HMM.probReport)
			System.out.println ("Arc to " + targetStateName + " " + probability);
	}

	void print () {
		System.out.println ("ARC TO " + targetStateName + " " + probability);
	}

	void store (PrintWriter stream) {
		stream.println ("ARC TO " + targetStateName + " " + count);
	}
}

