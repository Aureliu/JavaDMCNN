// -*- tab-width: 4 -*-
package Jet.NE;

import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

class SummarizedPatternFeature extends PatternFeature {
	public SummarizedPatternFeature(String prefix) {
		super(prefix);
	}

	public Instance pipe(Instance carrier) {
		TokenSequence tokens = (TokenSequence) carrier.getData();
		String prefix = getPrefix();

		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.getToken(i);
			String name = prefix + getSummarizedPattern(token.getText());
			token.setFeatureValue(name, 1.0);
		}

		return carrier;
	}

	private String getSummarizedPattern(String str) {
		int len = str.length();
		StringBuilder pattern = new StringBuilder();
		char lastType = '\0';

		for (int i = 0; i < len; i++) {
			char ch = str.charAt(i);
			char type = getType(ch);
			if (type != lastType) {
				pattern.append(type);
				lastType = type;
			}
		}

		return pattern.toString();
	}
}
