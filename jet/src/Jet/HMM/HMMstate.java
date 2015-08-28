// -*- tab-width: 4 -*-
package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Lisp.*;

/**
 *  a state of a Hidden Markov Model.
 */

public class HMMstate {

	/**
	 *  the name of the state.  This is used to refer to the state when loading,
	 *  printing, and storing an HMM.
	 */

	public String name;

	/**
	 *  the tag of the state.  The tag is used to associate a state with a
	 *  particular annotation on a document.  See the description of the tag table
	 *  in HMMAnnotate for further information.
	 */

	public String tag;
	private HMM hmm;
	private String featureName;
	private ArrayList allowedFeatureValues;
	HMMemitter emitter;
	HashSet arcset;
	HMMarc[] arcs;       // arcs[i] = arc with target i,
	                     // or null if no arc with this target
	int count;           // number of times this state was traversed in training

	public HMMstate (String name, String tag, Class emitterClass) {
		this.name = name;
		this.tag = tag;
		featureName = null;
		allowedFeatureValues = new ArrayList();
		try {
			emitter = (HMMemitter) emitterClass.newInstance();
			emitter.stateName = name;
		} catch (Exception e) {
			System.out.println ("Cannot create instance of HMMemitter " + emitterClass);
		}
		arcset = new HashSet();
		hmm = null;
	}

	/**
	 *  add arc <CODE>arc</CODE> to the set of arcs leaving this state.
	 */

	public void addArc (HMMarc arc) {
		arcset.add(arc);
	}

	/**
	 *  add an arc to the state named <CODE>arcName</CODE> to the set of
	 *  arcs leaving this state.
	 */

	public void addArc (String arcName) {
		addArc(new HMMarc(arcName, 0));
	}

	public void resolveNames (HashMap statesByName, int nStates) {
		arcs = new HMMarc[nStates];
		Iterator arcIterator = arcset.iterator();
		while (arcIterator.hasNext()) {
			HMMarc arc = (HMMarc) arcIterator.next();
			arc.resolveTarget(statesByName);
			arcs[arc.target] = arc;
		}
	}

	public void setHMM (HMM hmm) {
		this.hmm = hmm;
		emitter.hmm = hmm;
	}

	/**
	 *  initialize the state for training.
	 */

	public void resetForTraining () {
		count = 0;
		emitter.resetForTraining();
		Iterator arcIterator = arcset.iterator();
		while (arcIterator.hasNext()) {
			HMMarc arc = (HMMarc) arcIterator.next();
			arc.resetForTraining();
		}
	}

	/**
	 *  (during training), add 1 to the count of the times that 'token'
	 *  is emitted by this state.
	 */

	public void incrementEmitCount (String token, String priorToken, int n) {
		emitter.trainOnInstances(token, priorToken, n);
	}

	public void setCacheCount (String tag, int n) {
		emitter.setCacheCount(tag, n);
	}

	/**
	 *  imposes a feature constraint on tokens which match this state.  The
	 *  token must have feature named 's' with a value matching one of the
	 *  values specified through a call on 'allowFeatureValue'.
	 */

	public void setFeatureName (String s) {
		featureName = s;
	}

	/**
	 *  allow tokens for which feature tokenType == s to be emitted by
	 *  this state.
	 */

	public void addAllowedFeatureValue (String s) {
		allowedFeatureValues.add(s);
	}

	/**
	 *  returns true if token <CODE>token</CODE> can be emitted by this state.
	 *  This will be true if either no feature name has been specified,
	 *  , or if a feature name has been specified (through a call on
	 *  {@link #setFeatureName}), and that feature has one of the allowed
	 *  feature values (as specified by {@link #addAllowedFeatureValue}.
	 */

	public boolean allowedToken (Annotation token) {
		if (featureName == null) return true;
		return allowedFeatureValues.contains(token.get(featureName));
	}

	/**
	 *  compute the probabilities for token emission and arc transition
	 *  from the counts acquired during training.
	 */

	public void computeProbabilities () {
		if (HMM.probReport) {
			System.out.println ();
			System.out.println ("===== Computing probabilities for " + name);
			System.out.println ("state count = " + count);
		}
		// compute arc transition probabilities
		Iterator arcIterator = arcset.iterator();
		while (arcIterator.hasNext()) {
			HMMarc arc = (HMMarc) arcIterator.next();
			arc.computeProbabilities(count);
		}
		// compute emission probabilities
		emitter.computeProbabilities();
	}

	/**
	 *  prints a state, along with its tag, its feature constraint (if
	 *  any) its arcs, and its emission probabilities.
	 */

	public void print () {
		System.out.println("STATE " + name);
		if (tag != "") System.out.println ("TAG " + tag);
		if (featureName != null) {
			System.out.print ("FEATURE " + featureName);
			for (int i=0; i<allowedFeatureValues.size(); i++)
				System.out.print (" " + allowedFeatureValues.get(i));
			System.out.println();
		}
		Iterator arcIterator = arcset.iterator();
		while (arcIterator.hasNext())
			((HMMarc) arcIterator.next()).print();
		emitter.print();
	}

	/**
	 *  writes a state, along with its tag, its feature constraint (if any),
	 *  its arcs, and its emission probabilities, to 'stream', in a form
	 *  that can be reloaded by {@link HMM#load}.
	 */

	public void store (PrintWriter stream) {
		stream.println("STATE " + name + " " + count);
		if (tag != "") stream.println ("TAG " + tag);
		if (featureName != null) {
			stream.print ("FEATURE " + featureName);
			for (int i=0; i<allowedFeatureValues.size(); i++)
				stream.print (" " + allowedFeatureValues.get(i));
			stream.println();
		}
		Iterator arcIterator = arcset.iterator();
		while (arcIterator.hasNext())
			((HMMarc) arcIterator.next()).store(stream);
		emitter.store(stream);
	}

	/**
	 *  a preference (log probability) to be given to states with tag 'other'.
	 *  A positive value increases precision at a cost in recall.
	 */

	public static double otherPreference = 1.0;
	/**
	 *  returns the probability of emitting 'token' with attributes 'fs'
	 *  when in this state.
	 */

	public double getEmissionProb (String tokenText, String priorToken, Annotation token) {
		if (!allowedToken(token)) return HMM.UNLIKELY;
		// System.out.println ("Prob. of emitting " + token + " in state " + name +
		//                     " is " + emitter.getProbability(tokenText, priorToken, token.attributes()));
	    double p = emitter.getProbability(tokenText, priorToken, token.attributes());
	    // favor precision over recall
	    if (tag.equals("other")) p += otherPreference;
	    return p;
	}

	/**
	 *  returns the probability of a transition from this state to the
	 *  state numbered 'state' in the HMM.
	 */

	public double getTransitionProb (int state) {
		HMMarc arc = arcs[state];
		if (arc != null)
			return arc.probability;
		else
			return HMM.UNLIKELY;
	}
}
