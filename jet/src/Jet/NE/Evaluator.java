// -*- tab-width: 4 -*-
package Jet.NE;

import gnu.trove.TObjectIntHashMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class Evaluator {
	private static final String TOTAL = "\0";

	private TObjectIntHashMap correct = new TObjectIntHashMap();
	private TObjectIntHashMap miss = new TObjectIntHashMap();
	private TObjectIntHashMap gold = new TObjectIntHashMap();
	private TObjectIntHashMap system = new TObjectIntHashMap();

	public void evaluate(Document systemOut, Document goldOut) {
		Map<Span, String> goldEntities= makeSpanToEntityTypeMap(goldOut);
		Map<Span, String> extractedEntities = makeSpanToEntityTypeMap(systemOut);

		for (Map.Entry<Span, String> entry : goldEntities.entrySet()) {
			String type = entry.getValue();
			Span span = entry.getKey();
			String extractedType = extractedEntities.get(span);

			if (extractedType == null || !extractedType.equals(type)) {
				increment(miss, type);
			}
			increment(gold, type);
		}

		for (Map.Entry<Span, String> entry : extractedEntities.entrySet()) {
			String type = entry.getValue();
			Span span = entry.getKey();
			String correctType = goldEntities.get(span);

			if (correctType != null && correctType.equals(type)) {
				increment(correct, type);
			}
			increment(system, type);
		}
	}

	public Collection<String> getTypes() {
		String[] types = new String[gold.size() - 1];
		int i = 0;
		for (Object key : gold.keys()) {
			if (!key.equals(TOTAL)) {
				types[i++] = (String) key;
			}
		}
		return Arrays.asList(types);
	}

	public double getPrecision(String type) {
		return (double) correct.get(type) / system.get(type);
	}

	public double getPrecision() {
		return getPrecision(TOTAL);
	}

	public double getRecall(String type) {
		return (double) (gold.get(type) - miss.get(type)) / gold.get(type);
	}

	public double getRecall() {
		return getRecall(TOTAL);
	}

	private Map<Span, String> makeSpanToEntityTypeMap(Document doc) {
		Map<Span, String> entities = new HashMap<Span, String>();
		List<Annotation> names = doc.annotationsOfType("ENAMEX");

		for (Annotation name : names) {
			entities.put(name.span(), (String) name.get("TYPE"));
		}

		return entities;
	}

	private static void increment(TObjectIntHashMap map, Object key) {
		if (map.containsKey(key)) {
			map.increment(key);
		} else {
			map.put(key, 1);
		}

		if (map.containsKey(TOTAL)) {
			map.increment(TOTAL);
		} else {
			map.put(TOTAL, 1);
		}
	}
}
