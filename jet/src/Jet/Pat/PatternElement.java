// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

public abstract class PatternElement {

  public abstract String toString ();

  public abstract PatternGraph toGraph (Id id);

}
