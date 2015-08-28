// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.00
//Copyright:    Copyright (c) 2000
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Pat;

import java.io.*;
import Jet.Tipster.*;
import Jet.Console;

/**
 *  the encoding of the "print <I>message</I>" action, where <I>message</I>
 *  is a StringExpression (one or more strings or variables).
 */

public class PrintAction extends Action {

  StringExpression message;

  /**
   *  constructs a new PrintAction, given a message
   */

  public PrintAction (StringExpression message) {
    this.message = message;
  }

  /**
   *  constructs a new PrintAction by reading from StreamTokenizer <I>tok</I>.
   *  The input should be the token "write" (the current token) followed
   *  by a StringExpression:  one or more strings or variables,
   *  separated by "+".
   */

  public PrintAction (StreamTokenizer tok)
         throws IOException, PatternSyntaxError {
    tok.nextToken();
    message = new StringExpression(tok);
  }

  /**
   *  performs the "print" action, writing the message to the Console.
   */

  public int perform(Document doc, PatternApplication patap) {
    String stg = message.evaluate(doc, patap);
    Console.println (stg);
    return -1;
  }

  /**
   *   returns a printable form of the "print" action, consisting of
   *   "print" followed by the elements of the message, separated by "+".
   */

  public String toString() {
    return "print " + message;
  }
}
