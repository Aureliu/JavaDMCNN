// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.Set;

/**
 *  a dictionary used by the Extended Named Entity tagger.
 *
 *  @author  Akira Oda
 */

public abstract class Dictionary {
	public static class Entry {
		private int length;
		private Set<String> value;

		public Entry(int length, Set<String> value) {
			this.length = length;
			this.value = value;
		}

		public int getLength() {
			return length;
		}

		public Set<String> getValue() {
			return value;
		}
	}

	/**
	 *  initializes the dictionary look-up process for the text consisting of
	 *  tokens <code>tokens</code>.
	 */

	public abstract void lookupStart(String[] tokens);

	/**
	 *  look for a dictionary entry matching the token sequence beginning at
	 *  token <code>pos</code>.
	 *
	 *  @param  pos    the position within the token array passed to lookupStart
	 *
	 *  @return the longest dictionary entry matching the token sequence, or
	 *          <code>null</code> if no entry matches
	 */

	public abstract Dictionary.Entry lookup(int pos);

	/**
	 *  look for a dictionary entry matching the token sequence beginning at
	 *  position <code>pos</code> of token sequence <code>tokens</code>.
	 *
	 *  @return the longest dictionary entry matching the token sequence, or
	 *          <code>null</code> if no entry matches
	 */

	public Dictionary.Entry lookup(String[] tokens, int pos) {
		lookupStart(tokens);
		return lookup(pos);
	}
}
