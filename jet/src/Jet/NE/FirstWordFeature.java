// -*- tab-width: 4 -*-
package Jet.NE;

import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

public class FirstWordFeature extends Pipe {
	private String name;

	public FirstWordFeature(String name) {
		this.name = name;
	}

	public Instance pipe(Instance carrier) {
		TokenSequence tokens = (TokenSequence) carrier.getData();
		if (tokens.size() > 0) {
			Token token = tokens.getToken(0);
			token.setFeatureValue(name, 1.0f);
		}
		return carrier;
	}
}
