// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

/**
 *  procedure for looking a token sequence up in the Extended Named Entity
 *  dictionary.
 *
 *  @author  Akira Oda
 */

public class DictionaryTagger {

	Dictionary dict;

	public DictionaryTagger() {
	}

	/**
	 * Set dictionary.
	 *
	 * @param dict  the ENE dictionary
	 */

	public void setDictionary(Dictionary dict) {
		this.dict = dict;
	}

	/**
	 *  look up the tokens in <code>span</code> in the ENE dictionary and
	 *  record the results on the NE_INTERNAL annotations for these tokens.
	 *  The dictionary information is recorded on the 'categories' feature
	 *  of these annotations.
	 */

	public void annotate(Document doc, Span span) {
		List<Annotation> neTokens = doc
				.annotationsOfType("NE_INTERNAL", span);

		if (neTokens == null) {
			neTokens = Collections.emptyList();
		}

		String[] words = new String[neTokens.size()];
		for (int i = 0; i < words.length; i++) {
			words[i] = doc.normalizedText(neTokens.get(i));
		}

		int offset = 0;
		dict.lookupStart(words);
		while (offset < words.length) {
			Dictionary.Entry entry = dict.lookup(offset);

			if (entry != null) {
				for (String category : entry.getValue()) {
					NamedEntityAttribute attr = new NamedEntityAttribute(
							category, BioType.B);
					Set<NamedEntityAttribute> attrs = (Set<NamedEntityAttribute>) neTokens
							.get(offset).get("categories");

					attrs.add(attr);

					attr = new NamedEntityAttribute(category, BioType.I);
					for (int i = offset + 1; i < offset + entry.getLength(); i++) {
						attrs = (Set<NamedEntityAttribute>) neTokens
								.get(i).get("categories");
						attrs.add(attr);
					}
				}

				offset += entry.getLength();
			} else {
				++offset;
			}
		}
	}

	/**
	 *  look up all the tokens in 'doc' in the ENE dictionary.
	 */

	public void annotate(Document doc) {
		annotate(doc, new Span(0, doc.length()));
	}
}
