// -*- tab-width: 4 -*-
/**
 *
 */
package Jet.NE;

public class ChangeRule {
	private int index;
	private NamedEntityAttribute ne;

	public ChangeRule(int index, NamedEntityAttribute ne) {
		this.index = index;
		this.ne = ne;
	}

	public int getIndex() {
		return index;
	}

	public NamedEntityAttribute getNamedEntity() {
		return ne;
	}
}
