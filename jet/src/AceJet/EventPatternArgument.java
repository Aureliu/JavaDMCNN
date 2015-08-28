// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import java.io.*;

public class EventPatternArgument {

	String role;

	// either an Integer, representing the i-th node of the pattern,
	// or a String, indicating that the argument is to be obtained from
	// context.
	Object source;

	public EventPatternArgument (String role, Object source) {
		this.role = role;
		this.source = source;
	}

	public EventPatternArgument (String s) {
		if (s == null) {
			System.err.println ("EventPatternArgument: null constructor argument");
			return;
		}
		String[] fields = s.split(":");
		if (fields.length != 2) {
			System.err.println ("EventPatternArgument: invalid constructor argument: " + s);
			return;
		}
		role = fields[0];
		try {
			source = new Integer(fields[1]);
		} catch (NumberFormatException e) {
			System.err.println ("EventPatternArgument: invalid constructor argument: " + s);
		}
	}

	public String toString () {
		return role + ":" + source;
	}

	public void write (PrintWriter pw) {
		pw.println(toString());
	}

	public boolean equals (Object o) {
		if (!(o instanceof EventPatternArgument))
			return false;
		EventPatternArgument a = (EventPatternArgument) o;
		return role.equals(a.role) && source.equals(a.source);
	}

	public int hashCode () {
		return (role + source).hashCode();
	}

}
