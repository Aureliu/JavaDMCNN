// -*- tab-width: 4 -*-
package Jet.NE;

import java.util.Set;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

/**
 *  a transformation rule of the Extended Named Entity annotator.
 *
 *  @author  Akira Oda
 */

public class TransformRule {

	private MatchRuleItem[] ruleItems;

	private ChangeRule[] changeRules;

	private ChangeType changeType;

	/**
	 *  create a new TransformRule.
	 */

	public TransformRule() {
	}

	/**
	 *  set the left-hand (match) portion of the rule.
	 */

	public void setMatchRules(MatchRuleItem[] ruleItems) {
		this.ruleItems = ruleItems;
	}

	/**
	 *  set the right-hand (change) portion of the rule.
	 */

	public void setChangeRule(ChangeType type, ChangeRule[] changeRules) {
		this.changeType = type;
		this.changeRules = changeRules;
	}

	/**
	 *  return a count of the number of tokens to be matched by the left-hand
	 *  (match) portion of the rule.
	 */

	public int getPatternTokenCount() {
		return ruleItems.length;
	}

	/**
	 *  determines whether the left-hand side of the rule matches the tokens
	 *  beginning with token[pos].
	 *
	 *  @return  true if is does match, else false.
	 */

	public boolean accept(Document doc, Annotation[] tokens, int pos, ClassHierarchyResolver resolver) {
		if (pos + ruleItems.length > tokens.length) {
			return false;
		}

		for (int i = 0; i < ruleItems.length; i++) {
			if (!ruleItems[i].accept(doc, tokens, pos + i, resolver)) {
				return false;
			}
		}

		return true;
	}

	/**
	 *  applies the transformation (right-hand part) of the rule to the tokens
	 *  starting with token[pos].
	 */

	public void transform(Document doc, Annotation[] tokens, int pos) {
		if (!canApplyChangeRule(doc, tokens, pos)) {
			return;
		}

		for (ChangeRule change : changeRules) {
			int index = change.getIndex();
			Set<NamedEntityAttribute> attrs = (Set) tokens[pos + index].get("categories");
			attrs.clear();
			attrs.add(change.getNamedEntity());
		}
	}

	private boolean canApplyChangeRule(Document doc, Annotation[] tokens, int pos) {
	    if (changeType == ChangeType.FORCE) {
	    	return true;
	    }

		for (ChangeRule change : changeRules) {
			int n = change.getIndex();
			Set<NamedEntityAttribute> attrs = (Set) tokens[pos + n].get("categories");
			if (attrs.size() > 0) {
				return false;
			}
		}
		return true;
	}
}
