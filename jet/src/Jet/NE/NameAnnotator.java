// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

/**
 * NameAnnotator provides methods for (extended) named entity annotation
 * based on a dictionary and rules.
 *
 * @author Akira Oda
 */

public class NameAnnotator {
	private static final String systemName = "ENE";
	
	private DictionaryTagger dictTagger;
	private TransformRules rules;
	private ClassAnnotator classAnnotator;
	private ClassHierarchyResolver hierarchyResolver;
	private Map<String, String> aliasTable;

	/**
	 *  create a new NameAnnotator.
	 */

	public NameAnnotator() {
		dictTagger = new DictionaryTagger();
	}

	/**
	 *  specify the dictionary to be used by the ENE tagger.
	 */

	public void setDictionary(Dictionary dict) {
		dictTagger.setDictionary(dict);
	}
	
	/**
	 * load the the dictionary to be used by word class tagger.
	 */
	
	public void loadClassDictionary(Reader in) throws IOException {
		classAnnotator = new ClassAnnotator(in);
	}
	
	/**
	 * load the the dictionary to be used by word class tagger.
	 */

	public void loadClassDictionary(File file) throws IOException {
		classAnnotator = new ClassAnnotator(file);
	}

	/**
	 *  load the rules to be used by the ENE tagger using Reader 'in'.
	 */

	public void loadRules(Reader in) throws IOException, RuleFormatException {
		rules = TransformRules.load(in);
		if (hierarchyResolver != null) {
			rules.setClassHierarchyResolver(hierarchyResolver);
		}
	}

	/**
	 *  load the rules to be used by the ENE tagger using Reader 'in'.
	 */

	public void loadRules(File file) throws IOException, RuleFormatException {
		Reader in = null;
		try {
			in = new FileReader(file);
			loadRules(in);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ex) {
				}
			}
		}
	}

	/**
	 *  annotate the text in <code>span</code> with named entity (ENAMEX)
	 *  annotations using the dictionary and rules of the ENE tagger.  If
	 *  an alias map has been specified by 'setAliasMap', the tags are
	 *  translated using this map.
	 */

	public void annotate(Document doc, Span span) {
		NamedEntityUtil.splitToNamedEntity(doc, span);
		dictTagger.annotate(doc, span);
		if (classAnnotator != null) {
			classAnnotator.annotate(doc, span);
		}
		rules.apply(doc, span);
		NamedEntityUtil.packNamedEntity(doc, span, systemName);

		if (aliasTable != null) {
			List<Annotation> neList = doc.annotationsOfType("ENAMEX", span);
			for (Annotation name : neList) {
				String type = (String) name.get("TYPE");
				String alias = aliasTable.get(type);
				if (alias != null) {
					name.put("TYPE", alias);
				} else {
					doc.removeAnnotation(name);
				}
			}
		}
	}

	/**
	 *  annotate document <code>doc</code> with named entity (ENAMEX)
	 *  annotations using the dictionary and rules of the ENE tagger.  If
	 *  an alias map has been specified by 'setAliasMap', the tags are
	 *  translated using this map.
	 */

	public void annotate(Document doc) {
		annotate(doc, doc.fullSpan());
	}

	/**
	 *  specify a mapping to be used to translate the TYPE feature of ENAMEX
	 *  annotations as a final step in tagging.
	 */

	public void setAliasMap(Map<String, String> map) {
		this.aliasTable = map;
	}

	public void loadClassHierarchy(File file) throws IOException {
		hierarchyResolver = SimpleClassHierarchyResolver.getInstance(file);
		if (this.rules != null) {
			rules.setClassHierarchyResolver(hierarchyResolver);
		}
	}
}
