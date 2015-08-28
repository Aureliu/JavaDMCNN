// -*- tab-width: 4 -*-
package Jet.NE;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Vector;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

/**
 *  a list of transformation rules used by the ENE tagger.
 *
 *  @author  Akira Oda
 */

public class TransformRules {
	private List<TransformRule> rules;
	private ClassHierarchyResolver resolver;

	public TransformRules(List<TransformRule> rules, ClassHierarchyResolver resolver) {
		this.rules = rules;
		this.resolver = resolver;
	}

	public TransformRules(List<TransformRule> rules) {
		this.rules = rules;
		this.resolver = new ExactMatchClassHierarchyResolver();
	}

	/**
	 *  applies the transformation rules to 'span'.  At each token position, the
	 *  first rule (if any) which matches is applied.
	 */

	public void apply(Document doc, Span span) {
		Vector<Annotation> neList = doc.annotationsOfType("NE_INTERNAL", span);
		Annotation[] tokens = neList.toArray(new Annotation[0]);

		LOOP_POS: for (int i = 0; i < tokens.length; i++) {
			for (TransformRule rule : rules) {
				if (i + rule.getPatternTokenCount() > tokens.length) {
					continue;
				}

				if (rule.accept(doc, tokens, i, resolver)) {
					rule.transform(doc, tokens, i);
					continue LOOP_POS;
				}
			}
		}
	}

	public void setClassHierarchyResolver(ClassHierarchyResolver resolver) {
		this.resolver = resolver;
	}

	public ClassHierarchyResolver getClassHierarchyResolver() {
		return resolver;
	}

	/**
	 *  returns a count of the number of rules.
	 */

	public int getRuleCount() {
		return rules.size();
	}

	/**
	 *  read the transformation rules using reader 'in'.
	 */

	public static TransformRules load(Reader in) throws IOException, RuleFormatException {
		TransformRuleParser parser = new TransformRuleParser();
		List<TransformRule> rules = parser.parse(in);

		return new TransformRules(rules);
	}

	/**
	 *  loads the transformation rules from file 'file'.
	 */

	public static TransformRules load(File file) throws IOException, RuleFormatException {
		FileReader in = new FileReader(file);
		return load(in);
	}
}
