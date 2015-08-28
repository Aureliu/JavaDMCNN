// -*- tab-width: 4 -*-
package Jet.Util;

import java.util.Iterator;

/**
 * Utility class for manipulating String.
 *
 * @author Akira ODA
 */
public class StringUtils {

	/**
	 * Joins strings with separator.
	 *
	 * @param string
	 *            of separator
	 * @param array
	 *            array of object to be joined
	 * @return joined string
	 */
	public static String join(String glue, Object... array) {
		StringBuilder builder = new StringBuilder();

		if (array.length != 0) {
			builder.append(array[0]);
		} else {
			return "";
		}

		for (int i = 1; i < array.length; i++) {
			builder.append(glue);
			builder.append(array[i]);
		}

		return builder.toString();
	}

	/**
	 * Joins strings with separator.
	 *
	 * @param sep
	 *            string of separator
	 * @param iterable
	 *            <code>java.lang.Iterable</code> object to be joined
	 * @return joined string
	 */
	public static String join(String sep, Iterable<?> iterable) {
		Iterator<?> iter = iterable.iterator();

		if (!iter.hasNext()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		builder.append(iter.next());

		while (iter.hasNext()) {
			builder.append(sep);
			builder.append(iter.next());
		}

		return builder.toString();
	}
}
