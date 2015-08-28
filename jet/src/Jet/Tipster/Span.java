// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 1999
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Tipster;

/**
 *  A portion of a document, represented by its starting and ending
 *  character positions, and a pointer to the document.
 */

public class Span implements Comparable {
  int start, end;
  Document doc;

  /**
   *  Creates a span from position <I>s</I> up to position <I>e</I>,
   *  with a null document pointer.
   */

  public Span(int s, int e) {
    start = s;
    end = e;
    doc = null;
  }

  /**
   *  Returns the start of the span.
   */

  public int start () {
    return start;
  }

  /**
   *  Returns the end of the span.
   */

  public int end () {
    return end;
  }

  /**
   *  sets the start of the span to 's'.
   */

  public void setStart (int s) {
  	start = s;
  }

  /**
   *  sets the end of the span to 's'.
   */

  public void setEnd (int e) {
  	end = e;
  }

  /**
   *  Returns the end of the span, after trimming any white space at the
   *  end of the span.
   */

  public int endNoWS (Document doc) {
    int pos;
    for (pos=end;
         (pos>start)&&(Character.isWhitespace(doc.charAt(pos-1)));
         pos--);
    return pos;
  }

  /**
   *  Sets the Document associated with a Span.
   */

	public void setDocument (Document doc) {
		this.doc = doc;
	}

	/**
	 *  Returns the Document associated with a Span.
	 */

	public Document document () {
		return doc;
	}

  /**
   *  Returns true if the start and end of the spans are both equal.
   */

  public boolean equals (Object o) {
  	if (o instanceof Span) {
  		Span s = (Span) o;
  		return (start == s.start) && (end == s.end);
  	} else {
  		return false;
  	}
  }

  /**
   *  Returns a hashcode which is a function of the start and end values
   *  (so that, as required for hashing, equal spans have equal hashCodes.
   */

  public int hashCode () {
  	return start * 513 + end;
  }

  /**
   *  Returns true if Span 's' contains the span.
   */

  public boolean within (Span s) {
  	return (start >= s.start) && (end <= s.end);
  }

  /**
   *  compares this Span to Object o, which must be a Span.
   *  Returns -1 if the start of this span
   *  precedes the start of s, or they have the same start and the end of this
   *  span precedes the end of s.  Returns +1 if the start of this span follows
   *  the start of s, or they have the same start and the end of this span follows
   *  the end of s.  Otherwise returns 0.
   */

  public int compareTo (Object o) {
  	if (!(o instanceof Span)) throw new ClassCastException();
  	Span s = (Span) o;
  	if (start < s.start) return -1;
  	if (start > s.start) return +1;
  	if (end < s.end) return -1;
  	if (end > s.end) return +1;
  	return 0;
  }

  /**
   *  Returns a printable form of the span, "[start-end]".
   */

  public String toString () {
    return "[" + start + " - " + end + "]";
  }
}
