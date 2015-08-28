// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.Iterator;
import java.util.List;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import edu.umass.cs.mallet.base.pipe.iterator.AbstractPipeInputIterator;
import edu.umass.cs.mallet.base.types.Instance;

public class DocumentToSentenceIterator extends AbstractPipeInputIterator {
	private static final String SENTENCE = "sentence";

	private Document doc;

	private List<Annotation> sentences;

	private Iterator<Annotation> sentenceIter;

	private int index;

	public DocumentToSentenceIterator(Document doc, String textSegmentName,
			int firstIndex) {
		this.doc = doc;

		sentences = doc.annotationsOfType(SENTENCE);
		sentenceIter = sentences.iterator();
		this.index = firstIndex;
	}

	public DocumentToSentenceIterator(Document doc, String textSegmentName) {
		this(doc, textSegmentName, 1);
	}

	@Override
	public boolean hasNext() {
		return sentenceIter.hasNext();
	}

	@Override
	public Instance nextInstance() {
		Annotation sentence = sentenceIter.next();
		Instance carrier = new Instance(sentence.span(), null, "sentence" + index, doc);
		index++;
		return carrier;
	}
}
