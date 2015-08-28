// -*- tab-width: 4 -*-
package Jet.Lisp;

/**
 *  representation of a literal in the grammar, used to match
 *  a specific string.
 */

public class Literal {

	String stg;

	/**
	 *  creates a new literal for String 'stg'.
	 */

	public Literal (String stg) {
		this.stg = stg;
	}

	/**
	 *  returns the String associated with this literal.
	 */

	public String getString () {
		return stg;
	}

	/**
	 *  returns the String associated with this literal, enclosed in double quotes.
	 */

	public String toString () {
		return "\"" + stg + "\"";
	}
}
