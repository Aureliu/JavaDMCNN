// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.40
//Copyright:    Copyright (c) 2003, 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.HMM;

import java.util.*;
import java.io.*;
import Jet.Tipster.*;
import Jet.Lisp.*;
import Jet.Chunk.*;
import Jet.JetTest;

/**
 *  A Hidden Markov Model.  The model is composed of states (HMMstate) and arcs
 *  (HMMarc).  The model can be trained (train method), applied to a token sequence
 *  to find the most likely state sequence (viterbi method), loaded, saved, and
 *  printed.
 *  <p>  Note that this HMM assumes that tokens are emitted by states, not by arcs.
 *  However, the start and end states do not emit tokens, so a sequence of N tokens
 *  is matched by a sequence of N+2 states, including the start and end state.
 *  <p>  The HMM incorporates an auxiliary memory in the form of a document
 *  dictionary ('cache'), which is intended for use in name tagging.  If a word has once
 *  been tagged as a specific type of name ("Mr. John Park") within a document,
 *  this can be recorded so that subsequent uses of the name will be consistently
 *  tagged even if the context is ambiguous ("Park").
 *  <p> In addition to generating the best path with the Viterbi decoder,
 *  the decoder computes the margin (the difference in score between the best and
 *  second best path) and alternative (N-best) paths.  To obtain the margin,
 *  one must set <CODE>recordMargin</CODE> before calling <CODE>Viterbi</CODE>
 *  and <CODE>getMargin</CODE> after calling <CODE>Viterbi</CODE>.  To obtain
 *  N-best paths, one must set <CODE>setNbest</CODE> before calling <CODE>Viterbi</CODE>.
 *  Then, after calling <CODE>Viterbi</CODE>, each call on <CODE>nextBest</CODE>
 *  returns the next best path.
 */

public class HMM extends TokenClassifier {

	HashMap statesByName;
	ArrayList states, arcs;
	int startState, endState;
	Class emitterClass;
	HashSet cache;
	String[] tagsToCache;
	double smallestDifference = 0.;
	protected static final double UNLIKELY = -1.0E100;
	static boolean probReport = false;
	static boolean cacheTrace = false;

	private String excludedTag = null;
	int excludedTagStart = 0;
	int excludedTagEnd = 0;

	// the Viterbi decoding lattice
	private int nTokens;
	private double[][] pathProb;
	private int [][] backPointer;

	// data for generating N-best paths
	private SortedSet deviationSet;

	// tables for back-off (counts of tokens across all states)
	HashMap allStateTokenCount;
	HashMap allStateLCtokenCount;

	/**
	 *  after the viterbi decoder method has been invoked, the probability
	 *  along the best path found by the decoder.
	 */

	public double viterbiProbability = 0.;

	/**
	 *  after either the viterbi decode or the nextBest method has been
	 *  invoked, the probability along the most recently returned path.
	 */

	public double pathProbability = 0.;

	private double margin;
	private boolean recordMargin = false;
	private boolean recordLocalMargin = false;
	private boolean Nbest = false;

	/**
	 *  create a new HMM using instances of <CODE>BasicHMMemitter</CODE> to control
	 *  emission of tokens from states.
	 */

	public HMM () {
		this (BasicHMMemitter.class);
	}

	/**
	 *  create a new HMM using instances of <CODE>emitterClass</CODE> to control
	 *  emission of tokens from states.
	 */

	public HMM (Class emitterClass) {
		states = new ArrayList();
		statesByName = new HashMap();
		arcs = new ArrayList();
		startState = -1;
		endState = -1;
		this.emitterClass = emitterClass;
		tagsToCache = null;
		cache = new HashSet();
		allStateTokenCount = new HashMap();
		allStateLCtokenCount = new HashMap();
	}

	public void setTagsToCache (String[] tags) {
		tagsToCache = tags;
	}

	/**
	 *  read a description of an HMM from <CODE>HMMReader</CODE>.  The
	 *  description consists of lines <BR>
	 *    STATE  <I> state-name </I> <BR>
	 *    ARC TO <I> state-name </I> [<I> count </I>] <BR>
	 *    EMIT <I> token </I> [<I> count </I>] <BR>
	 *    TAG <I> token </I> <BR>
	 *    ALLOW <I> type </I>
	 */

	public void load (Reader HMMReader) throws IOException {
		String line, token;
		HMMstate currentState = null;
		BufferedReader reader = new BufferedReader (HMMReader);
		while ((line = reader.readLine()) != null) {
			try {
				StringTokenizer st = new StringTokenizer(line);
				if (!st.hasMoreTokens()) {
					// empty line
				} else if ((token = st.nextToken()).equalsIgnoreCase("state")) {
					// process "state <state name>"
					if (st.hasMoreTokens()) {
						String stateName = st.nextToken();
						currentState = new HMMstate(stateName, "", emitterClass);
						addState (currentState);
						currentState.resetForTraining();
					} else {
						throw new HMMerror("state name missing");
					}
				} else if (token.equalsIgnoreCase("arc")) {
					// process "arc to <state name> [<count>]"
					if (!(st.hasMoreTokens() && st.nextToken().equalsIgnoreCase("to"))) {
						throw new HMMerror("'to' missing");
					}
					if (!st.hasMoreTokens()) {
						throw new HMMerror("state name missing");
					}
					String stateName = st.nextToken();
					int count=1;
					if (st.hasMoreTokens()) {
						try {
							count = Integer.parseInt (st.nextToken());
						} catch (NumberFormatException e) {
							throw new HMMerror ("invalid count for arc");
						}
					}
					HMMarc arc = new HMMarc(stateName, count);
					arcs.add(arc);
					if (currentState == null) {
						throw new HMMerror("no initial state for arc");
					}
					currentState.addArc(arc);
					currentState.count += count;
				} else if (token.equalsIgnoreCase("emit")) {
					// process "emit <token> [[token] count]"
					if (!st.hasMoreTokens()) {
						throw new HMMerror("token missing");
					}
					String emit = st.nextToken();
					String priorEmit = "";
					if (currentState == null) {
						throw new HMMerror("no state for emit");
					}
					if (st.countTokens()>1) {
						priorEmit = emit;
						emit = st.nextToken();
					}
					if (st.hasMoreTokens()) {
						try {
							int count = Integer.parseInt (st.nextToken());
							currentState.incrementEmitCount(emit, priorEmit, count);
						} catch (NumberFormatException e) {
							throw new HMMerror ("invalid count for token");
						}
					} else {
						currentState.incrementEmitCount(emit, priorEmit, 1);
					}
				} else if (token.equalsIgnoreCase("feature")) {
					// process "feature <feature name> <feature value 1> <feature value 2> ..."
					if (!st.hasMoreTokens()) {
						throw new HMMerror("feature name missing");
					}
					String type = st.nextToken();
					if (currentState == null) {
						throw new HMMerror("no state for allow");
					}
					currentState.setFeatureName(type);
					while (st.hasMoreTokens()) {
						currentState.addAllowedFeatureValue(st.nextToken());
					}
				} else if (token.equalsIgnoreCase("tag")) {
					// process "tag <token>"
					if (!st.hasMoreTokens()) {
						throw new HMMerror("token missing");
					}
					String tag = st.nextToken();
					if (currentState == null) {
						throw new HMMerror("no state for tag");
					}
					currentState.tag = tag;
				} else if (token.equalsIgnoreCase("prevTagged")) {
					// process "prevTag <type> <count>"
					if (!st.hasMoreTokens()) {
						throw new HMMerror("token missing");
					}
					String tag = st.nextToken();
					if (st.hasMoreTokens()) {
						try {
							int count = Integer.parseInt (st.nextToken());
							currentState.setCacheCount(tag, count);
						} catch (NumberFormatException e) {
							throw new HMMerror ("invalid count for token");
						}
					} else {
						currentState.setCacheCount(tag, 1);
					}
				} else {
					throw new HMMerror("unrecognized input line");
				}
			} catch (HMMerror e) {
				System.out.println (">> " + line);
				System.out.println ("Error:  " + e.getMessage());
			}
		}
		resolveNames();
		computeProbabilities();
	}

	public void load (String fileName) {
		try {
			this.load (new BufferedReader
				(new InputStreamReader
					(new FileInputStream(fileName), JetTest.encoding)));
		} catch (IOException e) {
			System.out.println ("HMM.load:  Unable to load HMM");
			System.out.println (e);
		}
	}

	void resolveNames () {
		// check state names for arcs, add pointers to states
		Iterator stateIterator = states.iterator();
		while (stateIterator.hasNext())
			((HMMstate) stateIterator.next()).resolveNames(statesByName, states.size());
	}

	/**
	 *  add state <CODE>state</CODE> to the HMM.
	 */

	public void addState (HMMstate state) {
		String stateName = state.name;
		state.setHMM(this);
		statesByName.put(stateName,new Integer(states.size()));
		if (stateName.equalsIgnoreCase("start"))
			startState = states.size();
		if (stateName.equalsIgnoreCase("end"))
			endState = states.size();
		states.add(state);
	}

	/**
	 *  returns state with given name, or null if no such state
	 */

	public HMMstate getState (String stateName) {
		Integer i = (Integer) statesByName.get(stateName);
		if (i == null) return null;
		return (HMMstate) states.get(i.intValue());
	}

	public void resetForTraining () {
		Iterator stateIterator = states.iterator();
		while (stateIterator.hasNext())
			((HMMstate) stateIterator.next()).resetForTraining();
	}

	/**
	 * clears the name cache for the document.  Should be invoked before beginning
	 * the processing of a new document.
	 */

	public void newDocument() {
		cache = new HashSet();
	}

	void addToCache (String token, String type) {
		for (int i=0; i<tagsToCache.length; i++)
			if (type.equals(tagsToCache[i])) {
				cache.add(token + "|" + type);
				return;
			}
	}

	boolean inCache (String token, String type) {
		if (token.equalsIgnoreCase("the")) return false;  // << added Oct. 9
		if (token.equalsIgnoreCase("of")) return false;   // << added Oct. 9
		return cache.contains(token + "|" + type);
	}

	/**
	 *  a fast, simple algorithm for training the HMM.  This algorithm trains the
	 *  HMM from a fully annotated corpus and requires that at each token there
	 *  be exactly one arc which can be followed to a successor state with the
	 *  correct tag.
	 */

	public void train0 (Document doc, Annotation[] tokens, String[] tags) {
		int iState = startState;
		int nTokens = tokens.length;
		int nStates = states.size();
		String priorToken = "";
		for (int iToken = 0; iToken < nTokens; iToken++) {
			Annotation tokenAnn = tokens[iToken];
			String token = doc.text(tokenAnn).trim();
			String tag = tags[iToken];
			HMMstate state = (HMMstate) states.get(iState);
			state.count++;
			int iNext = -1;
			for (int is = 0; is < nStates; is++) {
				if (state.arcs[is] != null) {
					HMMstate nextState = (HMMstate) states.get(is);
					if (nextState.tag.equals(tag)) {
						if (iNext >= 0) {
							System.out.println ("Training error:  multiple successor states");
							System.out.print   ("from state " + state.name);
							System.out.println (" with tag " + tag);
						}
						iNext = is;
					}
				}
			}
			if (iNext < 0) {
				System.out.println ("Training error:  no successor states");
				System.out.print   ("from state " + state.name);
				System.out.println (" with tag " + tag);
				return;
			}
			state.arcs[iNext].count++;
			HMMstate nextState = (HMMstate) states.get(iNext);
			nextState.incrementEmitCount(token, priorToken, 1);
			if (tagsToCache != null)
				addToCache(token, tag);
			iState = iNext;
			priorToken = token;
		}
		HMMstate state = (HMMstate) states.get(iState);
		state.count++;
		if (state.arcs[endState] == null) {
			System.out.println ("Training error:  can't reach final state");
			System.out.println ("from state " + state.name);
			return;
		}
		state.arcs[endState].count++;
	}

	static final int NO_BACK_POINTER = -1;
	static final int MULTIPLE_BACK_POINTERS = -2;

	/**
	 *  a slower algorithm for training the HMM.  This algorithm trains the
	 *  HMM from a fully annotated corpus and requires that there be a unique
	 *  path through the network whose tags match those of the training data.
	 */

	public void train (Document doc, Annotation[] tokens, String[] tags) {
		int nStates = states.size();
		int nTokens = tokens.length;
		int [][] backPointer = new int[nTokens+1][nStates];
		for (int i=0; i<nStates; i++)
			backPointer[0][i] = NO_BACK_POINTER;
		backPointer[0][startState] = 0;
		// forward pass
		for (int iToken=1; iToken<=nTokens; iToken++) {
			Annotation token = tokens[iToken-1];
			String tag = tags[iToken-1];
			boolean successor = false;
			for (int iState=0; iState<nStates; iState++) {
				HMMstate state = (HMMstate) states.get(iState);
				int prior = NO_BACK_POINTER;
				if (state.tag.equals(tag) && state.allowedToken(token)) {
					for (int iPrior=0; iPrior<nStates; iPrior++) {
						HMMstate priorState = (HMMstate) states.get(iPrior);
						if (priorState.arcs[iState] != null &&
						    backPointer[iToken-1][iPrior] != NO_BACK_POINTER) {
						    successor = true;
						    if (prior == NO_BACK_POINTER)
						    	prior = iPrior;
						    else
						    	prior = MULTIPLE_BACK_POINTERS;
						}
					}
				}
				backPointer[iToken][iState] = prior;
			}
			if (!successor) {
				System.out.println ("HMM.train:  training error:  no successor states");
				System.out.print   ("at token " + doc.text(token).trim());
				System.out.println (" with tag " + tag);
				return;
			}
		}
		// transition to final state (last step of forward pass)
		if (endState < 0) {
			System.out.println ("No end state for HMM.");
			return;
		}

		int last = NO_BACK_POINTER;
		for (int iPrior=0; iPrior<nStates; iPrior++) {
			HMMstate priorState = (HMMstate) states.get(iPrior);
			if (priorState.arcs[endState] != null
			    && backPointer[nTokens][iPrior] != NO_BACK_POINTER) {
				if (last == NO_BACK_POINTER)
					last = iPrior;
				else
					last = MULTIPLE_BACK_POINTERS;
			}
		}
		// find path by backtracking (path includes start and end states)
		int[] path = new int[nTokens+2];
		path[nTokens+1] = endState;
		int iState = last;
		for (int iToken=nTokens; iToken>=0; iToken--) {
			if (iState == NO_BACK_POINTER) {
				System.out.println ("HMM.train:  no back pointer");
				System.out.println ("Error occurred at token " + iToken +
				                    " = " + doc.text(tokens[iToken-1]).trim());
				return;
			} else if (iState == MULTIPLE_BACK_POINTERS) {
				System.out.println ("HMM.train:  multiple back pointers");
				return;
			}
			path[iToken] = iState;
			// System.out.println ("path[" + iToken + "] = " + path[iToken]);
			iState = backPointer[iToken][iState];
		}
		// follow path, incrementing counts
		String priorToken = "";
		for (int iToken=0; iToken<=nTokens; iToken++) {
			iState = path[iToken];
			int iNext = path[iToken+1];
			HMMstate state = (HMMstate) states.get(iState);
			state.count++;
			state.arcs[iNext].count++;
			if (iToken > 0) {
				String token = doc.text(tokens[iToken-1]).trim();
				state.incrementEmitCount(token, priorToken, 1);
				if (tagsToCache != null) {
					String tag = tags[iToken-1];
					addToCache(token, tag);
				}
				priorToken = token;
			}

		}
	}

	/**
	 *  compute the probabilities for token emission and state transition from the
	 *  counts acquired in training.  This method should be invoked after training
	 *  is complete (after all calls on the train method) and before the HMM is
	 *  applied (calls on the viterbi method).
	 */

	public void computeProbabilities () {
		Iterator stateIterator = states.iterator();
		while (stateIterator.hasNext())
			((HMMstate) stateIterator.next()).computeProbabilities();
	}

	public void createModel () {
		computeProbabilities();
	}

	/**
	 *  print a complete description of the HMM (all states and arcs) to System.out.
	 */

	public void print() {
		Iterator stateIterator = states.iterator();
		while (stateIterator.hasNext())
			((HMMstate) stateIterator.next()).print();
	}

	/**
	 *  save the HMM to <CODE>stream</CODE> in a form which can be reloaded
	 *  using {@link #load}.
	 */

	public void store (PrintWriter stream) {
		Iterator stateIterator = states.iterator();
		while (stateIterator.hasNext())
			((HMMstate) stateIterator.next()).store(stream);
		stream.close();
	}

	public void store (String fileName) {
		try {
			this.store(new PrintWriter ( new BufferedWriter
				(new OutputStreamWriter
					(new FileOutputStream (fileName), JetTest.encoding))));
		} catch (IOException e) {
			System.out.println ("HMM.store:  unable to store HMM.");
			System.out.println (e);
		}
	}

	/**
	 *  a Viterbi decoder for HMMs.
	 *  Given an array of token annotations, <CODE>tokens</CODE>, on document
	 *  <CODE>doc</CODE>, returns the most likely path which can generate
	 *  those tokens.  The value returned is an array of the <B>states</B>
	 *  (indexes into <CODE>states</CODE>) along the most likely path.
	 */

	public int[] viterbiPath (Document doc, Annotation[] tokens) {
		viterbiProbability = 0.;
		int nStates = states.size();
		nTokens = tokens.length;
		pathProb = new double[nTokens+1][nStates];
		backPointer = new int[nTokens+2][nStates];
		double[][] secondBest = null;
		if (recordMargin | recordLocalMargin)
			secondBest = new double[nTokens+1][nStates];
		// initialize probabilities
		if (startState < 0) {
			System.out.println ("No start state for HMM.");
			return null;
		}
		for (int i=0; i < nStates; i++) {
			if (i == startState)
				pathProb[0][i] = 0.0;
			else
				pathProb[0][i] = UNLIKELY;
		}
		// induction (forward pass)
		String priorToken = "";
		for (int iToken=1; iToken<=nTokens; iToken++) {
			Annotation token = tokens[iToken-1];
			String tokenText = doc.text(token).trim();
			for (int iState=0; iState<nStates; iState++) {
				HMMstate state = (HMMstate) states.get(iState);
				double emitProb = state.getEmissionProb(tokenText, priorToken, token);
				double bestProb = UNLIKELY;
				double secondBestProb = UNLIKELY;
				int bestPrior = -1;
				for (int iPrior=0; iPrior<nStates; iPrior++) {
					HMMstate priorState = (HMMstate) states.get(iPrior);
					double prob = pathProb[iToken-1][iPrior] +
					              priorState.getTransitionProb(iState) + emitProb;
					if (prob > bestProb) {
						if (!recordLocalMargin) secondBestProb = bestProb;
						bestProb = prob;
						bestPrior = iPrior;
					}
					if (recordLocalMargin) {
						if (violatesConstraint(iToken-1, state)) {
							if (prob > secondBestProb) {
					  		secondBestProb = prob;
					  		// System.out.println ("SecondBestProb = " + secondBestProb);
					  	}
					  } else if (iToken > 1) {
				  		double prob2 = secondBest[iToken-1][iPrior] +
				    	               priorState.getTransitionProb(iState) + emitProb;
				    	if (prob2 > secondBestProb) {
				  			secondBestProb = prob2;
				  			// System.out.println ("SecondBestProb* = " + secondBestProb);
				  		}
					  }
					}
				}
				// System.out.println("Token: " + token);
				// System.out.println("pathProb["+iToken+"]["+iState+"="+state.name+"]="+bestProb);
				// System.out.println("backPointer["+iToken+"]["+iState+"]="+bestPrior);
				pathProb[iToken][iState] = bestProb;
				backPointer[iToken][iState] = bestPrior;
				if (recordMargin | recordLocalMargin)
					secondBest [iToken][iState] = secondBestProb;
			}
			priorToken = tokenText;
		}
		// transition to final state (last step of forward pass)
		if (endState < 0) {
			System.out.println ("No end state for HMM.");
			return null;
		}
		double bestProb = UNLIKELY;
		double secondBestProb = UNLIKELY;
		int bestPrior = -1;
		for (int iPrior=0; iPrior<nStates; iPrior++) {
			HMMstate priorState = (HMMstate) states.get(iPrior);
			double prob = pathProb[nTokens][iPrior] +
			              priorState.getTransitionProb(endState);
			if (prob > bestProb) {
				bestProb = prob;
				bestPrior = iPrior;
			}
			if (recordLocalMargin) {
				double prob2 = secondBest[nTokens][iPrior] +
				               priorState.getTransitionProb(endState);
				if (prob2 > secondBestProb) {
					secondBestProb = prob2;
				}
			}
		}
		backPointer[nTokens+1][endState] = bestPrior;
		// path readout by backtracking
		int[] path = new int[nTokens+2];
		path[nTokens+1] = endState;
		int iState = bestPrior;
		margin = -2. * UNLIKELY;
		for (int iToken=nTokens-1; iToken>=0; iToken--) {
			if (iState < 0) {
				// System.out.println ("HMM:  unable to decode sentence.");
				// System.out.print   ("      Sentence is: ");
				// for (int i=0; i<nTokens; i++) System.out.print (doc.text(tokens[i]));
				// System.out.println (" ");
				return null;
			}
			path[iToken+1] = iState;
			HMMstate state = (HMMstate)states.get(iState);
			String tag = state.tag;
			if (tagsToCache != null) {
				String token = doc.text(tokens[iToken]).trim();
				if (cacheTrace) System.out.println ("Adding " + token + " to cache with tag " + tag);
				addToCache(token, tag);
			}
			path[0] = startState;
			// System.out.println ("path[" + iToken + "] = " + path[iToken]);
			if ((recordMargin)&&
			    pathProb[iToken+1][iState] - secondBest[iToken+1][iState] < margin)
				margin = pathProb[iToken+1][iState] - secondBest[iToken+1][iState];
			iState = backPointer[iToken+1][iState];
		}
		viterbiProbability = bestProb;
		if (recordLocalMargin)
			margin = bestProb - secondBestProb;
		pathProbability = viterbiProbability;
		if (Nbest) {
			deviationSet = new TreeSet();
			addDeviations (path, viterbiProbability, nTokens+1);
		}
		return path;
	}

	/**
	 *  a Viterbi decoder for HMMs.
	 *  Given an array of token annotations, <CODE>tokens</CODE>, on document
	 *  <CODE>doc</CODE>, returns the most likely path which can generate
	 *  those tokens.  The value returned is an array of the <B>tags</B>
	 *  associated with the states along the most likely path.
	 */

	public String[] viterbi (Document doc, Annotation[] tokens) {
		int[] path = viterbiPath(doc, tokens);
		if (path == null) return null;
		int len = tokens.length;
		String[] pathTags = new String[len];
		for (int i=0; i<len; i++) {
			HMMstate state = (HMMstate)states.get(path[i+1]);
			pathTags[i] = state.tag;
		}
		return pathTags;
	}

	/**
	 *  after either the viterbi decode or the nextBest method has been
	 *  invoked, returns the probability along the most recently returned path.
	 */

	public double getPathProbability () {
		return pathProbability;
	}

	/**
	 *  enable the recording of the margin (the difference in score between the
	 *  best and second best analysis) by the Viterbi decoder.
	 */

	public void recordMargin() {
		recordMargin = true;
	}

	/**
	 *  if invoked after a call on 'viterbi', returns the margin (the difference
	 *  in score between the best and second best analyses).  Requires that
	 *  'recordMargin' be called at some point before the call on 'viterbi'.
	 */

	public double getMargin() {
		return margin;
	}

	/**
	 *  returns the margin for assigning a particular tag to a sequence of
	 *  tokens.  This procedure assumes that this assignment is part of the
	 *  Viterbi decoding of the sentence.  It computes the difference
	 *  between the log probability of the Viterbi decoding and the log
	 *  probability of the decoding using a constrained HMM where the
	 *  specified tokens cannot be assigned the tag <CODE>excludedTag</CODE>.
	 *
	 *  @param doc               the Document containing the sentence being tagged
	 *  @param tokens            the token annotations for the sentence
	 *  @param excludedTag       the tag assigned to the sequence
	 *  @param excludedTagStart  the index of the first token being assigned this tag
	 *  @param excludedTagEnd    the index of the last token being assigned this tag
	 */

	public double getLocalMargin (Document doc, Annotation[] tokens,
	                              String excludedTag, int excludedTagStart,
	                              int excludedTagEnd) {
	  // System.out.println ("Excluding " + excludedTag + " from " + excludedTagStart +
	  //                     " to " + excludedTagEnd);
		this.excludedTag = excludedTag;
		this.excludedTagStart = excludedTagStart;
		this.excludedTagEnd = excludedTagEnd;
		recordLocalMargin = true;
		viterbi (doc, tokens);
		recordLocalMargin = false;
		// System.out.println ("Margin = " + margin);
		return margin;
	}

	private boolean violatesConstraint (int iToken, HMMstate state) {
		return ((iToken == (excludedTagStart - 1)) && state.tag.equals(excludedTag)) ||
		       ((iToken >= excludedTagStart && iToken <= excludedTagEnd) &&
		         !state.tag.equals(excludedTag)) ||
		       ((iToken == (excludedTagEnd + 1)) && state.tag.equals(excludedTag));
	}

	private void addDeviations (int[] currentPath, double currentCost, int lastToken) {
  	for (int i=2; i<=lastToken; i++) {
  		addDeviationsAtToken (currentPath, currentCost, i);
  	}
  }

  /**
   *  adds deviations from the predecessors of token in currentPath (a part of
   *  N-best path generation).
   */

  private void addDeviationsAtToken (int[] currentPath, double currentCost, int token) {
  	// determine selected prior
  	int state = currentPath[token];
  	int bestPrior = backPointer[token][state];
  	HMMstate bestPriorState = (HMMstate) states.get(bestPrior);
  	// iterate over all priors EXCEPT selected one
  	for (int prior = 0; prior < states.size(); prior++) {
  		if (prior == bestPrior) continue;
  		if (prior == startState) continue;
  		if (prior == endState) continue;
  		HMMstate priorState = (HMMstate) states.get(prior);
  		if (priorState.getTransitionProb(state) == UNLIKELY) continue;
  		// compute cost of deviation path
  		double deviantCost = currentCost
  		       - /* cost of best path to best prior */ pathProb[token-1][bestPrior]
  		       - /* cost of arc from bestPrior to current */ bestPriorState.getTransitionProb(state)
   		       + /* cost of arc from prior to current */ priorState.getTransitionProb(state)
  		       + /* cost of best path to prior */ pathProb[token-1][prior];
  		// create deviation node
  		Deviation d = new Deviation (currentPath, token, prior, deviantCost);
  		deviationSet.add(d);
  		// System.out.println ("Adding deviation " + d);
  	}
  }

  /**
   *  enables N-best search.  This method must be called before calling
   *  <CODE>viterbi</CODE> if you intend to also call <CODE>nextBest</CODE>.
   */

  public void setNbest () {
  	Nbest = true;
  }

  /**
	 *  an N-best-paths generator for HMMs.  It assumes that <CODE>viterbiPath</CODE>
	 *  has already been called with an array of token annotations  and has
	 *  returned the most likely path.  Each subsequent call on <CODE>nextBest</CODE>
	 *  returns the next best (next most likely) path, or <CODE>null</CODE> if no
	 *  further paths can be found.  The value returned is an array of the <B>states</B>
	 *  (indexes into <CODE>states</CODE>) along the most likely path.
	 */

	public int[] nextBestPath () {
		if (deviationSet == null || deviationSet.isEmpty())
			return null;
		// find best deviation
		Deviation d = (Deviation) deviationSet.last();
		// System.out.println ("Trying deviation " + d);
		deviationSet.remove(d);
		int[] basePath = d.basePath;
		int token = d.token;
		int[] newPath = new int[nTokens+2];
		// duplicate path array past deviation point
		for (int i=token; i<nTokens+2; i++)
			newPath[i] = basePath[i];
		// create path array from deviation point to start
		int newPrior = d.prior;
		for (int i=token-1; i>=0; i--) {
			newPath[i] = newPrior;
			newPrior = backPointer[i][newPrior];
			// if we can't build this path, give up
			if (newPrior < 0)
				return null;
		}
		// build deviations for new path
		addDeviations (newPath, d.cost, token-1);
		pathProbability = d.cost;
		return newPath;
	}

	/**
	 *  an N-best-paths generator for HMMs.  It assumes that <CODE>viterbi</CODE>
	 *  has already been called with an array of token annotations  and has
	 *  returned the most likely path.  Each subsequent call on <CODE>nextBest</CODE>
	 *  returns the next best (next most likely) path, or <CODE>null</CODE> if no
	 *  further paths can be found.  The value returned is an array of the <B>tags</B>
	 *  associated with the states along the most likely path.
	 */

	public String[] nextBest () {
		int[] path = nextBestPath ();
		if (path == null) return null;
		String[] pathTags = new String[nTokens];
		for (int i=0; i<nTokens; i++) {
			HMMstate state = (HMMstate)states.get(path[i+1]);
			pathTags[i] = state.tag;
		}
		return pathTags;
	}

	/**
	 *  a possible modification of a path through the lattice, and the cost of the
	 *  modified path.  An instance represents the possibility of changing the
	 *  state of the token <i>preceeding</i> <CODE>token</CODE> to <CODE>prior</CODE>
	 *  in path <CODE>basePath</CODE>.  The probability of the resulting path
	 *  is <CODE>cost</CODE>.
	 */

	private class Deviation implements Comparable {

		int[] basePath;
		int token;
		int prior;
		double cost;

		public Deviation (int[] basePath, int token, int prior, double cost) {
			this.basePath = basePath;
			this.token = token;
			this.prior = prior;
			this.cost = cost;
		}

		public int compareTo (Object o) {
			if (o instanceof Deviation) {
				Deviation d2 = (Deviation) o;
				double cost2 = d2.cost;
				if (cost < cost2)
					return -1;
				else if (cost > cost2)
					return +1;
				else
					return 0;
			} else {
				throw new ClassCastException();
			}
		}

		public String toString () {
			return "Change prior of token " + token + " to " + prior + " in" + pathString(basePath);
		}

	}

	/**
	 *  tests the Viterbi decoder and the N-best path generator on a simple
	 *  4-word noun phrase using a 4-state HMM.
	 */

	public static void main (String[] args) {
		HMM testHMM = makeTestHMM();
		Document d = new Document("big big cat nap");
		Annotation[] tokens = new Annotation[4];
		tokens[0] = new Annotation("token", new Span(0, 4), null);
		tokens[1] = new Annotation("token", new Span(4, 8), null);
		tokens[2] = new Annotation("token", new Span(8, 12), null);
		tokens[3] = new Annotation("token", new Span(12, 15), null);
		d.addAnnotation(tokens[0]);
		d.addAnnotation(tokens[1]);
		d.addAnnotation(tokens[2]);
		d.addAnnotation(tokens[3]);
		testHMM.setNbest();
		int[] path = testHMM.viterbiPath (d, tokens);
		double prob = testHMM.getPathProbability();
		System.out.println ("Best path:   " + pathString(path) + " with probability " + prob);
		int[] path2 = testHMM.nextBestPath();
		prob = testHMM.getPathProbability();
		System.out.println ("Second path: " + pathString(path2) + " with probability " + prob);
		int[] path3 = testHMM.nextBestPath();
		prob = testHMM.getPathProbability();
		System.out.println ("Third path:  " + pathString(path3) + " with probability " + prob);
		int[] path4 = testHMM.nextBestPath();
		prob = testHMM.getPathProbability();
		System.out.println ("Fourth path: " + pathString(path4) + " with probability " + prob);
		int[] path5 = testHMM.nextBestPath();
		prob = testHMM.getPathProbability();
		System.out.println ("Fifth path:  " + pathString(path5) + " with probability " + prob);
	}

	/**
	 *  create a test HMM with four states, START, ADJ, NOUN, and END, and a
	 *  few words in each state.
	 */

	private static HMM makeTestHMM () {
		HMM testHMM = new HMM(BasicHMMemitter.class);
		HMMstate startState = new HMMstate("start", "", BasicHMMemitter.class);
		HMMstate adjState   = new HMMstate("adj", "JJ", BasicHMMemitter.class);
		HMMstate nounState  = new HMMstate("noun", "NN", BasicHMMemitter.class);
		HMMstate endState   = new HMMstate("end",   "", BasicHMMemitter.class);
		testHMM.addState(startState);
		testHMM.addState(adjState);
		testHMM.addState(nounState);
		testHMM.addState(endState);
		testHMM.resetForTraining();
		startState.addArc(new HMMarc("adj", 3));
		startState.addArc(new HMMarc("noun", 1));
		adjState.addArc  (new HMMarc("noun", 3));
		adjState.addArc  (new HMMarc("adj", 1));
		nounState.addArc (new HMMarc("noun", 1));
		nounState.addArc (new HMMarc("end", 4));
		adjState.incrementEmitCount("safe", "", 1);
		adjState.incrementEmitCount("big", "", 3);
		nounState.incrementEmitCount("cat", "", 2);
		nounState.incrementEmitCount("nap", "", 1);
		nounState.incrementEmitCount("safe", "", 1);
		nounState.incrementEmitCount("cracker", "", 1);
		startState.count = 4;
		adjState.count   = 4;
		nounState.count  = 5;
		testHMM.resolveNames();
		testHMM.computeProbabilities();
		return testHMM;
	}

	static String pathString (int[] path) {
		if (path == null)
			return " null ";
		String s = " [";
		for (int i=0; i<path.length; i++)
			s += " " + path[i];
		s += " ] ";
		return s;
	}

	class HMMerror extends Exception {
		HMMerror (String s) {
			super(s);
		}
	}
}
