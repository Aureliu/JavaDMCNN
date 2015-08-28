// -*- tab-width: 4 -*-
package Jet.Parser;

import Jet.Tipster.*;
import Jet.Lisp.*;

import java.util.*;
import javax.swing.tree.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 *  a graphical display of a parse tree.
 *  <p>
 *  The input tree is a tree of Annotations.  Each non-terminal node is an
 *  Annotation with a 'children' attribute, whose value is an array of
 *  Annotations.
 */

public class ParseView extends JFrame {

	private JScrollPane jScrollPane = new JScrollPane();
	private JParseComponent parseComponent;

	/**
	 *  creates a new Frame entitled 'title' displaying the parse tree rooted
	 *  at 'root'.
	 */

	public ParseView(String title, Annotation root) {
    super (title);
    setSize (400, 300);
    parseComponent = new JParseComponent();
    jScrollPane.getViewport().add(parseComponent);
    getContentPane().add(jScrollPane, BorderLayout.CENTER);
    buildMenus();
    this.setVisible(true);
    parseComponent.init (root);
  }

  public static String[] relations = {"none", "subject", "object", "of", "apposite", "headC",
                                      "pp", "preName", "nameMod"};
	public static String[] features = {"none", "head"};
	private int i;
	private String relationToShow;
	private String featureToShow;

	private void buildMenus () {
	  JMenuBar menuBar = new JMenuBar();
		// build relation menu
		JMenu relationMenu = new JMenu ("relations");
		for (i=0; i<relations.length; i++) {
			JMenuItem showRelation = new JMenuItem (relations[i]);
	    showRelation.addActionListener(
	      new ActionListener() {
	      	int j;
	      	JParseComponent pc;
	      	{j = i;
	      	 pc = parseComponent;}
	        public void actionPerformed (ActionEvent e) {
	          relationToShow = relations[j];
						pc.repaint();
	        }
	      }
	    );
	    relationMenu.add(showRelation);
	  }
    menuBar.add(relationMenu);
    // build feature menu
    JMenu featureMenu = new JMenu ("features");
		for (i=0; i<features.length; i++) {
			JMenuItem showRelation = new JMenuItem (features[i]);
	    showRelation.addActionListener(
	      new ActionListener() {
	      	int j;
	      	JParseComponent pc;
	      	{j = i;
	      	 pc = parseComponent;}
	        public void actionPerformed (ActionEvent e) {
	          featureToShow = features[j];
	          pc.layOutTree();
						pc.repaint();
	        }
	      }
	    );
	    featureMenu.add(showRelation);
	  }
    menuBar.add(featureMenu);
    setJMenuBar(menuBar);
  }

  /**
   *  the JComponent within the ParseView which actually displays the tree.
   */

	protected class JParseComponent extends JComponent {

  	// a list of all the nodes in the tree
  	ArrayList nodes = new ArrayList();
  	// the (x, y) position of each node in the tree
		HashMap position = new HashMap();
		FontMetrics fm;
		private int height;
		private int verticalSeparation;
		private int horizontalSeparation;
		private int maxX, maxY;
		// the root of the tree
		private Annotation root;

  	JParseComponent () {
  	}

		/**
		 *  initialize the tree:  compute the position of each node.
		 */

  	void init (Annotation root) {
	    fm = getGraphics().getFontMetrics();
	    horizontalSeparation = fm.charWidth('m');
	    height = fm.getHeight();
	    verticalSeparation = height * 2;
	    this.root = root;
	    layOutTree();
	  }

	  void layOutTree () {
	  	maxX = 0;
	    maxY = 0;
	    computePosition(root, 10, 10);
	    setPreferredSize(new Dimension(maxX + 50, maxY + 50));
	  }

  	/**
  	 *  compute the position of parse tree node 'root', where 'x' and 'y'
  	 *  are the coordinates of the upper left hand corner of the rectangle
  	 *  within which 'root' and its subtree will be displayed.  The
  	 *  position of 'root' is the position of the horizontal center
  	 *  of the baseline of the name of node root.
  	 */

	  int computePosition (Annotation root, int x, int y) {
	  	nodes.add(root);
	  	int childrenWidth = 0;
	  	Annotation[] children = ParseTreeNode.children(root);
	  	if (children != null) {
	  		for (int i=0; i<children.length; i++) {
	  		 int w = computePosition(children[i], x+childrenWidth, y + height + verticalSeparation);
	  		 childrenWidth += w + horizontalSeparation;
	  		}
		  	childrenWidth -= horizontalSeparation;
	  	} else {
	  		Jet.Tipster.Document doc = root.span().document();
	  		String word = doc.text(root).trim();
	  		childrenWidth = fm.stringWidth(word);
	  	}
	  	String nodeName = nodeName(root);
	  	int localWidth = fm.stringWidth(nodeName);
	  	int width = Math.max(childrenWidth, localWidth);
	  	position.put(root, new Point(x + (width / 2), y));
	  	maxX = Math.max(maxX, x);
	  	maxY = Math.max(maxY, y);
	  	return width;
	  }

	  /**
	   *  draw the parse tree (once the positions of the nodes have
	   *  been calcuated by 'init'.
	   */

	  public void paintComponent (Graphics g) {
			super.paintComponent(g);
			for (int i=0; i<nodes.size(); i++) {
				Annotation node = (Annotation) nodes.get(i);
				Point p = (Point) position.get(node);
				String nodeName = nodeName(node);
				int nodeWidth = fm.stringWidth(nodeName);
				g.drawString(nodeName, p.x-(nodeWidth/2), p.y);
				Annotation[] children = ParseTreeNode.children(node);
		  	if (children != null) {
		  		for (int j=0; j<children.length; j++) {
		  			Annotation child = children[j];
		  			Point pChild = (Point) position.get(child);
		  			g.drawLine(p.x, p.y+2, pChild.x, pChild.y-height);
		  		}
		  	} else {
		  		Jet.Tipster.Document doc = node.span().document();
		  		String word = doc.text(node).trim();
		  		int wordWidth = fm.stringWidth(word);
		  		g.setColor(Color.blue);
		  		g.drawString(word, p.x-(wordWidth/2), p.y+height+verticalSeparation);
		  		g.drawLine (p.x, p.y+2, p.x, p.y+verticalSeparation);
		  		g.setColor(Color.black);
		  	}
		  	displayRelation (node, p, g);
	  	}
		}

		private String nodeName (Annotation node) {
			String cat = (String) node.get("cat");
			if (featureToShow == "head") {
				String head = SynFun.getImmediateHead(node);
				if (head != null)
					return cat + " [" + head + "]";
			}
			return cat;
		}

		private void displayRelation (Annotation node, Point p, Graphics g) {
			if (relationToShow == null)
				return;
			Object target = node.get(relationToShow);
			if (target == null || !(target instanceof Annotation))
				return;
			Point targetPosn = (Point) position.get(target);
			g.setColor(Color.red);
	 		g.drawLine (p.x+4, p.y+2, targetPosn.x+4, targetPosn.y-height);
	 		g.setColor(Color.black);
 		}
	}
}
