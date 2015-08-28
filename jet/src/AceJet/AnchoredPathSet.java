package AceJet;

import java.util.*;
import java.io.*;

public class AnchoredPathSet {

	ArrayList<AnchoredPath> paths = new ArrayList<AnchoredPath>();
	Map<String, List<AnchoredPath>> pathIndex = new HashMap<String, List<AnchoredPath>>();
	Map<String, List<AnchoredPath>> argIndex = new HashMap<String, List<AnchoredPath>>();

	public AnchoredPathSet (String fileName) throws IOException {
		BufferedReader reader = new BufferedReader (new FileReader (fileName));
		String line;
		int count = 0;
		while ((line = reader.readLine()) != null) {
			AnchoredPath p = new AnchoredPath(line);
			paths.add(p);
			if (pathIndex.get(p.path) == null)
				pathIndex.put(p.path, new ArrayList<AnchoredPath>());
			pathIndex.get(p.path).add(p);
			String args = p.arg1 + ":" + p.arg2;
			if (argIndex.get(args) == null)
				argIndex.put(args, new ArrayList<AnchoredPath>());
			argIndex.get(args).add(p);
			count++;
		}
		System.out.println ("Loaded " + count + " paths.");
	}

	public List<AnchoredPath> getByPath (String path) {
		return pathIndex.get(path);
	}

	public List<AnchoredPath> getByArgs (String arg1, String arg2) {
		return getByArgs (arg1 + ":" + arg2);
	}

	public List<AnchoredPath> getByArgs (String args) {
		return argIndex.get(args);
	}
	
}
