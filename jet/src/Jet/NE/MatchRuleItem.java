// -*- tab-width: 4 -*-
package Jet.NE;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

/**
 * an element of the left-hand (match) portion of an ENE transformation rule,
 * specifying constraints on a single token.
 * 
 * @author Akira Oda
 */

public class MatchRuleItem {
	private StringRule stringRule;

	private ClassRule classRule;

	private PartOfSpeechRule partOfSpeechRule;

	private NamedEntityRule namedEntityRule;

	private RegexpRule regexpRule;

	public ClassRule getClassRule() {
		return classRule;
	}

	public void setClassRule(ClassRule classRule) {
		this.classRule = classRule;
	}

	public NamedEntityRule getNamedEntityRule() {
		return namedEntityRule;
	}

	public void setNamedEntityRule(NamedEntityRule namedEntityRule) {
		this.namedEntityRule = namedEntityRule;
	}

	public PartOfSpeechRule getPartOfSpeechRule() {
		return partOfSpeechRule;
	}

	public void setPartOfSpeechRule(PartOfSpeechRule partOfSpeechRule) {
		this.partOfSpeechRule = partOfSpeechRule;
	}

	public StringRule getStringRule() {
		return stringRule;
	}

	public void setStringRule(StringRule stringRule) {
		this.stringRule = stringRule;
	}

	public RegexpRule getRegexpRule() {
		return regexpRule;
	}

	public void setRegexpRule(RegexpRule regexpRule) {
		this.regexpRule = regexpRule;
	}

	public boolean accept(Document doc, Annotation[] tokens, int pos,
			ClassHierarchyResolver resolver) {

		if (stringRule != null && !stringRule.accept(doc, tokens, pos)) {
			return false;
		}

		if (classRule != null && !classRule.accept(doc, tokens, pos, resolver)) {
			return false;
		}

		if (partOfSpeechRule != null
				&& !partOfSpeechRule.accept(doc, tokens, pos)) {
			return false;
		}

		if (namedEntityRule != null
				&& !namedEntityRule.accept(doc, tokens, pos)) {
			return false;
		}
		
		if (regexpRule != null && !regexpRule.accept(doc, tokens, pos)) {
			return false;
		}

		return true;
	}
}
