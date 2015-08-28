// -*- tab-width: 4 -*-
package Jet.NE;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

class PatternFeature extends Pipe {
	private String prefix;

	public PatternFeature(String prefix) {
		this.prefix = prefix;
	}

	protected String getPrefix() {
		return prefix;
	}

	public Instance pipe(Instance carrier) {
		TokenSequence tokens = (TokenSequence) carrier.getData();
		int len = tokens.size();
		String prefix = getPrefix();

		for (int i = 0; i < len; i++) {
			Token token = tokens.getToken(i);
			String name = prefix + getPattern(token.getText());
			token.setFeatureValue(name, 1.0);
		}

		return carrier;
	}

	private String getPattern(String str) {
		int len = str.length();
		char[] pattern = new char[len];
		for (int i = 0; i < len; i++) {
			pattern[i] = getType(str.charAt(i));
		}
		return new String(pattern);
	}

	protected char getType(char ch) {
		if (Character.isUpperCase(ch)) {
			return 'A';
		} else if (Character.isLowerCase(ch)) {
			return 'a';
		} else if (Character.isDigit(ch)) {
			return '0';
		} else {
			return ch;
		}
	}
}
