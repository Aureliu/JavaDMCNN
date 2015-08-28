// -*- tab-width: 4 -*-
package Jet.Tipster;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.colorchooser.*;
import Jet.JetTest;

/**
 *  provides a mechanism for associating particular highlighting colors with
 *  particular annotation types in Document displays.  The color information
 *  is retained in a color file.  Colors may either be associated with a type
 *  or with a type together with the value of one of its features.
 */

public class AnnotationColor {

	public static ArrayList<AnnotationColorEntry> colors = new ArrayList<AnnotationColorEntry>();
	static File colorFile;
	static JFrame colorFrame = null;
	// alpha factor (roughly, intensity) for colors
	static final int ALPHA = 150;

	/**
	 *  initializes the colorFrame -- the menu which is used to choose
	 *  colors for each annotation type.  The color choice is initialized
	 *  from file 'annColors.clr' in directory 'dataPath'.  An application
	 *  should invoke the constructor once to initialize the color choices.
	 */

	public AnnotationColor (String dataPath) {
		this (dataPath, "annColors.clr");
	}

	/**
	 *  initializes the colorFrame -- the menu which is used to choose
	 *  colors for each annotation type.  The color choice is initialized
	 *  from file 'colorFileName' in directory 'dataPath'.  An application
	 *  should invoke the constructor once to initialize the color choices.
	 */

	public AnnotationColor (String dataPath, String colorFileName) {
		try {
			colorFile = new File(dataPath, colorFileName);
			if (!colorFile.createNewFile()) {
				System.out.println ("Reading colors from file " + colorFile);
				readColors();
			}
			if (!JetTest.batchFlag) {
				colorFrame = new JFrame("Customize Annotation Color");
				colorFrame.getContentPane().setLayout(new GridLayout(0, 1));
				colorFrame.addWindowListener(new WindowAdapter() {
						public void windowClosing(WindowEvent e) {
							colorFrame.dispose();
						}
					});
				fillColorFrame();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 *  read a table of Annotation type/color pairs, one per line, from 'colorFile'.
	 *  Each line should have the form <br>
	 *    		type    color <br>
	 *  or <br>
	 *		type    feature    featureValue    color <br>
	 *  where 'type' is the name of the type, and 'color' is an integer representing a color;
	 *  if present, 'feature' and 'featureValue' specialize the color to Annotations
	 *  with that feature/featureValue combination.  The color may be optionally followed
	 *  by a (single-character) field;  this character is used by AnnotationTool for
	 *  annotating text with that type and featureValue.
	 */

	public static void readColors() {
		try {
			colors.clear();
			BufferedReader in = new BufferedReader(new FileReader(colorFile));
			String line;
			while ((line = in.readLine()) != null) {
				AnnotationColorEntry ace = AnnotationColorEntry.read(line);
				if (ace != null)
					colors.add(ace);
			}
		}
		catch (FileNotFoundException e) {}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 *  write a table of type/color pairs, one per line, to 'colorFile'.
	 */

	public static void writeColors() {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(colorFile)));
			for (AnnotationColorEntry c : colors)
				out.println (c.toString());
			out.flush();
		}
		catch (IOException e) {
			System.err.println("Error writing to file " + colorFile);
		}
	}

	/**
	 *  add a color entry for annotation type 'type'.  Initialize it to a
	 *  random color and then display the color frame to offer the user a
	 *  choice for changing it.
	 */

	public static void addType(String type) {
		for (AnnotationColorEntry c : colors)
			if (c.type.equals(type))
				return;
		String color = "0x" + Integer.toHexString(type.hashCode() & 0xffffff);
		colors.add(new AnnotationColorEntry (type, null, null, color, ' '));
		if (colorFile != null) {
			writeColors();
			if (!JetTest.batchFlag)
				showColors();
		}
	}

	/**
	 *  fills colorFrame with a column of buttons, one for each
	 *  annotation type/feature combination.
	 */

	private static void fillColorFrame() {
		JButton button;
		colorFrame.getContentPane().removeAll();
		for (int i=0; i<colors.size(); i++) {
			final int buttonNumber = i;
			AnnotationColorEntry c = colors.get(i);
			button = new JButton(c.typeAndFeature());
			try {
				button.setBackground(setAlpha(Color.decode(c.color), ALPHA));
			} catch (NumberFormatException e) {
				System.err.println ("Invalid color for annotation type " + c.typeAndFeature());
			}
			button.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						JButton activeButton = (JButton) e.getSource();
						Color newColor = JColorChooser.showDialog(colorFrame,
											  "Choose Background Color",
											  activeButton.getBackground());
						if (newColor != null) {
							activeButton.setBackground(newColor);
							colors.get(buttonNumber).color =
								"0x" + Integer.toHexString(newColor.hashCode() & 0xffffff);
							writeColors();
						}
					}
				});
			colorFrame.getContentPane().add(button);
		}
	}

	public static void showColors() {
		readColors();
		fillColorFrame();
		colorFrame.pack();
		colorFrame.setVisible(true);
	}

	/**
	 *  returns the Color associated with Annotation ann, or null if there
	 *  is no Color association for this Annotation.
	 */

	public static Color getColor (Annotation ann) {
		String annType = ann.type();
		for (AnnotationColorEntry c : colors) {
			if (annType.equals(c.type) &&
				(c.feature == null ||
				 ann.get(c.feature).equals(c.featureValue))) {
				try {
					return setAlpha (Color.decode(c.color), ALPHA);
				} catch (NumberFormatException e) {
					System.err.println ("Invalid color for annotation type " 
							    + c.typeAndFeature());
				}
			}
		}
		return null;
	}

	private static Color setAlpha (Color c, int alpha) {
		return new Color (c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}

/**
 *  an association between an Annotation type / feature and a color.
 */

class AnnotationColorEntry {
	String type;
	String feature;
	String featureValue;
	String color;
	char key;

	AnnotationColorEntry (String tp, String f, String fv, String c, char k) {
		type = tp;
		feature = f;
		featureValue = fv;
		color = c;
		key = k;
	}

	static AnnotationColorEntry read (String line) {
		String annType;
		String feature = null;
		String fv = null;
		String color;
		char key = ' ';
		StringTokenizer stok = new StringTokenizer(line);
		if (stok.hasMoreTokens())
			annType = stok.nextToken();
		else
			return null;
		if (stok.countTokens() == 0) {
			color = "0x" + Integer.toHexString(annType.hashCode() & 0xffffff);
		} else {
			if (stok.countTokens() > 2) {
				feature = stok.nextToken();
				fv = stok.nextToken();
			}
			color = stok.nextToken();
			if (stok.countTokens() > 0) {
				String keyString = stok.nextToken();
				if (keyString.length() > 0)
					key = keyString.charAt(0);
			}
		}
		return new AnnotationColorEntry (annType, feature, fv, color, key);
	}

	public String toString () {
		if (feature == null) {
			return type + " " + color + " " + key;
		} else {
			return type + " " + feature + " " + featureValue + " " + color + " " + key;
		}
	}

	public String typeAndFeature () {
		if (feature == null) {
			return type;
		} else {
			return type + " " + feature + " " + featureValue;
		}
	}
}
