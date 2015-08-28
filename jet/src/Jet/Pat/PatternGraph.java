// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.util.Vector;

public class PatternGraph {
  public Vector inEdges;
  public Vector outEdges;

  public PatternGraph(Vector in, Vector out) {
    inEdges = in;
    outEdges = out;
  }

  public void setOutEdges (PatternNode target) {
    for (int i=0; i<outEdges.size(); i++) {
      ((PatternArc) outEdges.get(i)).target = target;
    }
  }

  public PatternArc[] inEdgeArray () {
    return (PatternArc[]) inEdges.toArray(new PatternArc[0]);
  }
}
