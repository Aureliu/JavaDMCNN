// -*- tab-width: 4 -*-
package Jet.NE;

public class ExactMatchClassHierarchyResolver implements
		ClassHierarchyResolver {

	public boolean isSubClassOf(String target, String className) {
		return className.equals(target);
	}
}
