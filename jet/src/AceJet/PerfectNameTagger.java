// -*- tab-width: 4 -*-
package AceJet;

import java.util.*;
import Jet.Tipster.*;
import Jet.Lisp.FeatureSet;
import Jet.Scorer.NameTagger;

/**
 *  contains methods which create perfect name (ENAMEX) annotations from
 *  an APF file.
 */

public class PerfectNameTagger implements NameTagger {

	AceDocument aceDoc;
	NameTagger realTagger;

	/**
	 *  create a PerfectNameTagger based on the information in AceDocument
	 *  'aceDoc'.
	 */

	PerfectNameTagger (AceDocument aceDoc, NameTagger realTagger) {
		this.aceDoc = aceDoc;
		this.realTagger = realTagger;
	}

	/**
	 *  tag Span 'span' of Document 'doc' with ENAMEX annotations.
	 */

	public void tag (Document doc, Span span) {
		// assign a constit cat=name annotation if either
		//  1) APF file has a name annotation
		//  2) APF file has a PRE annotation and it corresponds to an ENAMEX annotation
		//     produced by the name tagger (only needed for Ace 2004 data)
		if (hasPreTags(span))
			realTagger.tag(doc, span);
		for (AceEntity entity : aceDoc.entities) {
			ArrayList perfectMentions = entity.mentions;
			for (int j=0; j<perfectMentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) perfectMentions.get(j);
				Span head = mention.jetHead;
				if (!head.within(span))
					continue;
				String type = mention.type;
				// PRE:  for 2004 files only
				if (type.equals("PRE")) {
					Vector enamexes = doc.annotationsAt(head.start(), "ENAMEX");
					if (enamexes != null) {
						Annotation enamex = (Annotation) enamexes.get(0);
						if (enamex.end() == head.end()) {
							type = "NAM";
						}
					}
				}
				if (type.equals("NAM")) {
					// stretch 'head' span to next token boundary
					// (annotation end may not align with Jet token boundaries)
					int start = head.start();
					int posn = start;
					int end = head.end();
					while (posn < end) {
						Annotation token = doc.tokenAt(posn);
						if (token == null)
							// if we can't find start of token, use given jetExtent
							posn = end;
						else
							posn = token.end();
					}
					Span s = new Span(start, posn);
					Annotation name = new Annotation("ENAMEX", s, new FeatureSet("TYPE", entity.type));
					doc.addAnnotation(name);
				}
			}
		}
	}

	private boolean hasPreTags (Span span) {
		for (AceEntity entity : aceDoc.entities) {
			ArrayList perfectMentions = entity.mentions;
			for (int j=0; j<perfectMentions.size(); j++) {
				AceEntityMention mention = (AceEntityMention) perfectMentions.get(j);
				Span head = mention.jetHead;
				if (!head.within(span))
					continue;
				if (mention.type.equals("PRE"))
					return true;
			}
		}
		return false;
	}
		

	/**
	 *  tag the entire Document 'doc' with ENAMEX annotations.
	 */

	public void tagDocument (Document doc) {
		tag (doc, doc.fullSpan());
	}

	/**
	 * included to conform to the NameTagger interface.
	 * performs no operation for this class.
	 */

	public void load (String fileName) {
	}

	/**
	 * included to conform to the NameTagger interface.
	 * performs no operation for this class.
	 */

	public void newDocument () {
	}
}
