// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

/**
 *  Utility methods for the Extended Named Entity annotator.
 *
 *  @author  Akira Oda
 */

public class NamedEntityUtil {

	/**
	 *  create NE_INTERNAL annotations for use by the Extended Named Entity
	 *  annotator.  One annotation is created for each token in <CODE>span</CODE>.
	 *  Information from existing 'tagger' and 'ENAMEX' annotations is captured in
	 *  the 'pos' and 'categories' features on the NE_INTERNAL annotation.
	 *  Existing ENAMEX annotations are deleted.
	 *
	 *  @param doc   document containing annotations
	 *  @param span  annotations within this span are processed
	 */

	public static void splitToNamedEntity(Document doc, Span span) {
		Vector<Annotation> tokenList = doc.annotationsOfType("token", span);
		for (Annotation token : tokenList) {
			Set<NamedEntityAttribute> categories = new HashSet<NamedEntityAttribute>();
			FeatureSet f = new FeatureSet();
			f.put("categories", categories);
			doc.annotate("NE_INTERNAL", token.span(), f);
		}

		// set Part-Of-Speech attribute
		Vector<Annotation> posList = doc.annotationsOfType("tagger", span);
		if (posList != null) {
			for (Annotation pos : posList) {
				Vector<Annotation> neList = doc.annotationsAt(pos.start(),
						"NE_INTERNAL");
				if (neList != null && neList.size() > 0) {
					for (Annotation ne : neList) {
						ne.put("pos", pos.get("cat"));
					}
				}
			}
		}

		// set named entity attributes
		Vector<Annotation> enamexList = doc.annotationsOfType("ENAMEX", span);
		if (enamexList != null) {
			for (Annotation enamex : enamexList) {
				Vector<Annotation> neList = doc.annotationsOfType(
						"NE_INTERNAL", enamex.span());
				String type = (String) enamex.get("TYPE");
				NamedEntityAttribute attrB = new NamedEntityAttribute(type,
						BioType.B);
				NamedEntityAttribute attrI = new NamedEntityAttribute(type,
						BioType.I);

				{
					Set<NamedEntityAttribute> categories = (Set) neList.get(0)
							.get("categories");
					categories.add(attrB);
				}

				for (int i = 1; i < neList.size(); i++) {
					Set<NamedEntityAttribute> categories = (Set) neList.get(i)
							.get("categories");
					categories.add(attrI);
				}
			}

			for (Annotation enamex : enamexList) {
				doc.removeAnnotation(enamex);
			}
		}
	}

	public static void splitToNamedEntity(Document doc) {
		splitToNamedEntity(doc, doc.fullSpan());
	}

	/**
	 *  create ENAMEX annotations from the NE_INTERNAL annotations used internally
	 *  by the Extended Named Entity annotator.
	 *
	 *  @param doc    document containing annotations
	 *  @param span   annotations within this span are processed
	 *  @param system named entity system name
	 */

	public static void packNamedEntity(Document doc, Span span, String system) {
		Vector<Annotation> namedEntityList = doc
				.annotationsOfType("NE_INTERNAL");

		if (namedEntityList == null) {
			return;
		}

		int offset = 0;
		while (offset < namedEntityList.size()) {
			Set<NamedEntityAttribute> categories = (Set<NamedEntityAttribute>) namedEntityList
					.get(offset).get("categories");

			if (categories == null || categories.size() == 0) {
				++offset;
				continue;
			}

			NamedEntityAttribute category = null;
			for (NamedEntityAttribute attr : categories) {
				if (attr.getBioType() == BioType.B) {
					category = attr;
					break;
				}
			}

			if (category == null) {
				++offset;
				continue;
			}

			NamedEntityAttribute followAttrbute = new NamedEntityAttribute(
					category.getCategory(), BioType.I);
			int len = 1;
			while (offset + len < namedEntityList.size()) {
				Annotation ne = namedEntityList.get(offset + len);
				Set<NamedEntityAttribute> attrs = (Set<NamedEntityAttribute>) ne
						.get("categories");
				if (!attrs.contains(followAttrbute)) {
					break;
				} else {
					++len;
				}
			}

			FeatureSet f = new FeatureSet();
			f.put("TYPE", category.getCategory());
			if (system != null) {
				f.put("SYSTEM", system);
			}
			int start = namedEntityList.get(offset).start();
			int end = namedEntityList.get(offset + len - 1).end();
			doc.annotate("ENAMEX", new Span(start, end), f);
			offset += len;
		}

		for (Annotation a : namedEntityList) {
			doc.removeAnnotation(a);
		}
	}

	public static void packNamedEntity(Document doc, String system) {
		packNamedEntity(doc, doc.fullSpan(), system);
	}
}
