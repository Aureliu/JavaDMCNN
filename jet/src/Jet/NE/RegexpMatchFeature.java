// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.regex.Pattern;

/**
 * Feature generator class which generates if token matches with regular
 * expression.
 *
 * @author Akira ODA
 */
public class RegexpMatchFeature extends BooleanFeature {
	private Pattern regexp;

	public RegexpMatchFeature(String featureName, String regexp, int flag) {
		super(featureName);
		this.regexp = Pattern.compile(regexp, flag);
	}

	public RegexpMatchFeature(String featureName, String regexp) {
		this(featureName, regexp, 0);
	}

	@Override
	public boolean matches(String word) {
		return regexp.matcher(word).matches();
	}
}
