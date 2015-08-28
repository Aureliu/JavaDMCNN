// -*- tab-width: 4 -*-
package Jet.NE;

/**
 * Attributes of named entity.
 *
 * NamedEntityAttributes includes named entity type and BioType.
 *
 * @author Akira ODA
 */
public final class NamedEntityAttribute implements
		Comparable<NamedEntityAttribute> {
	String category;

	private BioType bio;

	public NamedEntityAttribute(String category, BioType bio) {
		this.category = category;
		this.bio = bio;
	}

	public String getCategory() {
		return category;
	}

	public BioType getBioType() {
		return bio;
	}

	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (!(obj instanceof NamedEntityAttribute)) {
			return false;
		}

		NamedEntityAttribute other = (NamedEntityAttribute) obj;


		if (category == null) {
			return other.category == null && bio == other.bio;
		} else {
			return category.equals(other.category) && bio == other.bio;
		}
	}

	public int compareTo(NamedEntityAttribute other) {
		if (this == other) {
			return 0;
		} else if (other == null) {
			return 1;
		}

		int cmp;
		if (this.category == other.category) {
			cmp = 0;
		} else if (this.category == null) {
			cmp = -1;
		} else if (other.category == null) {
			cmp = 1;
		} else {
			cmp = category.compareTo(other.category);
		}

		if (cmp != 0) {
			return cmp;
		}

		return bio.compareTo(other.bio);
	}

	public int hashCode() {
		int constant = 37;
		int total = 17;

		if (category != null) {
			total = total * constant + category.hashCode();
		}
		total = total * constant + bio.hashCode();

		return total;
	}

	public String toString() {
		StringBuilder str = new StringBuilder();
		switch (bio) {
		case B:
			str.append("B-");
			break;

		case I:
			str.append("I-");
			break;
			
		case N:
			str.append("N-");
			break;

		case O:
			return "O";
			
		default:
			// unreachable
			throw new InternalError();
		}

		str.append(category);
		return str.toString();
	}
}
