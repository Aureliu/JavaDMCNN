// -*- tab-width: 4 -*-
package Jet.Chunk;

import Jet.Tipster.*;

/**
 *  a trainable classifier for assigning tags to a sequence of tokens
 */

public abstract class TokenClassifier {

	public abstract void train (Document doc, Annotation[] tokens, String[] tags);

	public abstract void createModel ();

	public abstract void store (String fileName);

	public abstract void load (String fileName);

	public abstract String[] viterbi (Document doc, Annotation[] tokens);

	public String[] nextBest () {
		return null;
	}

	public double getLocalMargin (Document doc, Annotation[] tokens,
	                              String excludedTag, int excludedTagStart,
	                              int excludedTagEnd) {
		return 0.0;
	}

	public void recordMargin() {
	}

	public double getPathProbability() {
		return 0.0;
	}

	public double getMargin() {
		return 0.0;
	}

	public void newDocument () {
	}

}
