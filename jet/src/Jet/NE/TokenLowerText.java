// -*- tab-width: 4 -*-
package Jet.NE;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

public class TokenLowerText extends Pipe {
	private String prefix;

	public TokenLowerText(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public Instance pipe(Instance carrier) {
		TokenSequence tokens = (TokenSequence) carrier.getData();

		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.getToken(i);
			String name = prefix + token.getText().toLowerCase();
			token.setFeatureValue(name, 1.0);
		}

		return carrier;
	}
}
