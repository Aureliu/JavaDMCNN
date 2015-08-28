// -*- tab-width: 4 -*-
package Jet.NE;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

/**
 * Feature generator class which generates if token is numerical format.
 *
 * @author Akira ODA
 */
public class NumericalFeatures extends BooleanFeature {
	private NumberFormat format;

	public NumericalFeatures(String featureName, Locale locale) {
		super(featureName);
		if (locale == null) {
			format = NumberFormat.getInstance(Locale.US);
		} else {
			format = NumberFormat.getInstance(locale);
		}
	}

	public NumericalFeatures(String featureName) {
		this(featureName, null);
	}

	public boolean matches(String word) {
		if (word.length() == 0) {
			return false;
		}

		ParsePosition pos = new ParsePosition(0);
		format.parse(word, pos);
		if (pos.getIndex() == word.length()) {
			return true;
		} else {
			return false;
		}
	}
}
