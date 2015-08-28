// -*- tab-width: 4 -*-
package Jet.NE;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

class AlphaFeature extends Pipe {
	private String prefix;

	public AlphaFeature(String prefix) {
		this.prefix = prefix;
	}

	public Instance pipe(Instance carrier) {
		TokenSequence tokens = (TokenSequence) carrier.getData();

		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.getToken(i);
			StringBuilder buffer = new StringBuilder(prefix);

			String word = token.getText();
			for (int j = 0; j < word.length(); j++) {
				char ch = word.charAt(j);
				if (Character.isLowerCase(ch) || Character.isUpperCase(ch)) {
					buffer.append(ch);
				}
			}
			if (buffer.length() > prefix.length()) {
				token.setFeatureValue(buffer.toString(), 1.0);
			}
		}

		return carrier;
	}
}
