// -*- tab-width: 4 -*-
package Jet.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import Jet.Parser.DependencyAnalyzer;
import Jet.Parser.ParseTreeNode;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class ChunkDependencyAnalyzer {
	private TreeBasedChunker chunker;

	private DependencyAnalyzer dependencyAnalyzer;

	public ChunkDependencyAnalyzer() {
		chunker = new TreeBasedChunker();
		dependencyAnalyzer = new DependencyAnalyzer();
	}

	public void analyzeChunkDependency(Document doc, ParseTreeNode tree) {
		chunker.chunk(doc, tree);
		dependencyAnalyzer.resolveTerminalDependency(tree);
		List<Annotation> terminals = getHeadMarkedTerminals(tree);
		List<Annotation> chunks = doc.annotationsOfType("chunk", tree.ann.span());
		Map<Annotation, Annotation> terminalToAnnotationMap = makeTerminalToChunkMap(chunks, terminals);
		
		for (Annotation term : terminals) {
			if (term.get("isHead") != Boolean.TRUE) {
				continue;
			}
			Annotation head = (Annotation) term.get("dep");
			if (head == null) {
				continue;
			}

			Annotation chunk = terminalToAnnotationMap.get(term);
			Annotation depChunk = terminalToAnnotationMap.get(head);
			if (chunk != null && depChunk != null && chunk != depChunk) {
				chunk.put("dep", depChunk);
			}
		}

		for (Annotation chunk : chunks) {
			Annotation depChunk = (Annotation) chunk.get("dep");
			if (depChunk != null) {
				continue;
			}
			
			if (hasHeadTerminal(doc, chunk)) {
				continue;
			}

			for (Annotation term : doc.annotationsOfType("constit", chunk.span())) {
				Annotation dep = (Annotation) term.get("dep");
				if (dep != null && dep != term) {
					depChunk = terminalToAnnotationMap.get(dep);
					if (depChunk != null) {
						chunk.put("dep", depChunk);
						break;
					}
				}
			}
		}
		
		removeHeadMark(terminals);
	}

	private static Map<Annotation, Annotation> makeTerminalToChunkMap(List<Annotation> chunks, List<Annotation> terminals) {
		Map<Annotation, Annotation> terminalToChunkMap = new IdentityHashMap<Annotation, Annotation>();

		for (Annotation term : terminals) {
			for (Annotation chunk : chunks) {
				if (term.span().within(chunk.span())) {
					terminalToChunkMap.put(term, chunk);
					break;
				}
			}
		}

		return terminalToChunkMap;
	}

	private static List<Annotation> getHeadMarkedTerminals(ParseTreeNode tree) {
		if (tree.children == null) {
			return Collections.singletonList(tree.ann);
		} else {
			List<Annotation> list = new ArrayList<Annotation>();

			int i = 1;
			for (ParseTreeNode child : tree.children) {
				if (child.children != null) {
					list.addAll(getHeadMarkedTerminals(child));
				} else {
					Annotation terminal = child.ann;
					boolean isHead = (i == tree.head);
					terminal.put("isHead", isHead);
					list.add(terminal);
				}
				i++;
			}

			return list;
		}
	}
	
	private static void removeHeadMark(List<Annotation> terminals) {
		for (Annotation terminal : terminals) {
			terminal.remove("isHead");
		}
	}
	
	private static boolean hasHeadTerminal(Document doc, Annotation chunk) {
		List<Annotation> nodes = doc.annotationsOfType("constit", chunk.span());
		if (nodes == null) {
			return false;
		}
		
		for (Annotation node : nodes) {
			if (node.get("isHead") == Boolean.TRUE) {
				return true;
			}
		}
		
		return false;
	}
}
