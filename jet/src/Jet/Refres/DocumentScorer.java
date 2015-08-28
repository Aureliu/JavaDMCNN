// -*- tab-width: 4 -*-
package Jet.Refres;

import Jet.Tipster.*;

/**
 *  abstract class for coreference scorers.
 */

public abstract class DocumentScorer {

	/**
	 *  compute a coreference score between two documents,
	 *  <CODE>responseDoc</CODE> and <CODE>keyDoc</CODE>.
	 */

	public abstract void score (Document responseDoc, Document keyDoc);

	/**
	 *  report to standard output the score for the most recently
	 *  processed document pair.
	 */

	public abstract void report ();

	/**
	 *  report the overall score for all documents processed so far.
	 */

	public abstract void summary ();

}
