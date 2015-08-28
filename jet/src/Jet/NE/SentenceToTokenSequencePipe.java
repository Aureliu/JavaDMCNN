// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.Collections;
import java.util.List;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.LabelAlphabet;
import edu.umass.cs.mallet.base.types.LabelSequence;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;

public class SentenceToTokenSequencePipe extends Pipe {
	public SentenceToTokenSequencePipe() {
		super(null, LabelAlphabet.class);
	}

	@Override
	public Instance pipe(Instance carrier) {
		Document doc = (Document) carrier.getSource();
		Span span = (Span) carrier.getData();

		List<Annotation> names = doc.annotationsOfType("ENAMEX", span);
		if (names == null) {
			names = Collections.emptyList();
		}
		Annotation.sortByStartPosition(names);

		TokenSequence data = new TokenSequence();
		LabelSequence target = new LabelSequence(
				(LabelAlphabet) getTargetAlphabet());

		int pos = span.start();
		for (Annotation name : names) {
			if (name.start() > pos) {
				addTokens(data, target, doc, new Span(pos, name.start()), "O");
			}
			addTokens(data, target, doc, name.span(), (String) name.get("TYPE"));
			pos = name.end();
		}

		if (pos < span.end()) {
			addTokens(data, target, doc, new Span(pos, span.end()), "O");
		}

		carrier.setData(data);
		carrier.setSource(data);
		carrier.setTarget(target);
		carrier.setProperty("document", doc);
		carrier.setProperty("span", span);

		return carrier;
	}

	private void addTokens(TokenSequence data, LabelSequence target,
			Document doc, Span span, String label) {
		List<Annotation> tokens = doc.annotationsOfType("token", span);
		if (tokens == null) {
			return;
		}

		Annotation.sortByStartPosition(tokens);

		if (label.equals("O")) {
			// out of named entity
			for (Annotation token : tokens) {
				data.add(makeToken(doc, token));
				target.add(label);
			}
		} else {
			// in named entity
			data.add(makeToken(doc, tokens.get(0)));
			target.add("B-" + label);

			String followingLabel = "I-" + label;
			for (int i = 1; i < tokens.size(); i++) {
				Annotation token = tokens.get(i);
				data.add(makeToken(doc, token));
				target.add(followingLabel);
			}
		}
	}

	private Token makeToken(Document doc, Annotation token) {
		Token t = new Token(doc.normalizedText(token));
		t.setProperty("span", token.span());
		return t;
	}
}
