package Jet.NE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class ClassAnnotator {
	private Map<String, Collection<NamedEntityAttribute>> dictionary;

	/**
	 * Constructs class annotator.
	 * @param in
	 * @throws IOException
	 */
	public ClassAnnotator(Reader in) throws IOException {
		BufferedReader reader;
		if (in instanceof BufferedReader) {
			reader = (BufferedReader) in;
		} else {
			reader = new BufferedReader(in);
		}

		Map<String, NamedEntityAttribute> classNames = new IdentityHashMap<String, NamedEntityAttribute>();

		dictionary = new HashMap<String, Collection<NamedEntityAttribute>>();

		String line;
		while ((line = reader.readLine()) != null) {
			String[] tmp = line.split("\t");
			String word = tmp[0];
			Collection<NamedEntityAttribute> classes = new ArrayList<NamedEntityAttribute>(tmp.length - 1);
			for (int i = 1; i < tmp.length; i++) {
				String className = tmp[i].intern();
				if (classNames.containsKey(className)) {
					classes.add(classNames.get(className));
				} else {
					NamedEntityAttribute cls = new NamedEntityAttribute(className, BioType.N);
					classes.add(cls);
					classNames.put(className, cls);
				}
			}
			dictionary.put(word, classes);
		}
	}
	
	public ClassAnnotator(File file) throws IOException {
		this(new FileReader(file));
	}
	
	public void annotate(Document doc, Span span) {
		Vector<Annotation> neTokens = doc.annotationsOfType("NE_INTERNAL", span);
		for (Annotation neToken : neTokens) {
			String token = doc.normalizedText(neToken);
			Collection<NamedEntityAttribute> entries = dictionary.get(token);
			if (entries != null) {
				Set<NamedEntityAttribute> categories = (Set<NamedEntityAttribute>) neToken
						.get("categories");
				categories.addAll(entries);
			}
		}
	}
}
