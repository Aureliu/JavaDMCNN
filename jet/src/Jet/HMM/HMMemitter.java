// -*- tab-width: 4 -*-
package Jet.HMM;

import java.io.*;
import Jet.Lisp.FeatureSet;

/**
 *  this abstract class specifies the methods required for any
 *  class which computes the emission probabilities for an HMM.
 */

public abstract class HMMemitter {

	public HMM hmm;
	public String stateName;

	/**
	 *  initialize the emitter prior to training.  This method
	 *  should be called before any calls on {@link #trainOnInstances}.
	 */

	public abstract void resetForTraining ();

	/**
	 *  update emission counts to indicate that String <CODE>token</CODE>
	 *  appeared <CODE>n</CODE> times as an output of the current state.
	 */

	public abstract void trainOnInstances (String token, String priorToken, int n);

	/**
	 *  computate probabilities of emission from counts.  This method
	 *  will be called after all calls on {@link #trainOnInstances} and
	 *  before calls on {@link #getProbability}.
	 */

	public abstract void computeProbabilities ();

	/**
	 *  returns the probability that the current HMM state will emit
	 *  token <CODE>token</CODE> with FeatureSet <CODE>fs</CODE>.
	 */

	public abstract double getProbability (String token, String priorToken, FeatureSet fs);

	public void setCacheCount (String type, int n) {
	};

	/**
	 *  print the information about emission from this state to System.out.
	 */

	public abstract void print ();

	/**
	 *  write the information about emission from this state in a form
	 *  which can be read by {@link HMM#load}.
	 */

	public abstract void store (PrintWriter stream);
}
