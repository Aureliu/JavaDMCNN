// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import Jet.Lisp.FeatureSet;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class SimpleTimeRule extends TimeRule {
	private DateTimeFormatter formatter;

	public void setParameters(Map params) {
		super.setParameters(params);
		this.formatter = DateTimeFormat.forPattern((String) params.get("format"));
	}

	public void apply(Document doc, List<Object> values, Span span, DateTime ref) {
		Map params = getParameters();
		String value = (String) params.get("value");
		String diff = (String) params.get("diff");
		String dir = (String) params.get("dir");
		DateTime val = ref;
		if (value != null) {
			value = assignValues(value, values);
			val = new DateTime(value);
		} else if (diff != null) {
			diff = assignValues(diff, values);
			Period period = new Period(diff);

			if (dir == null || dir.equals("plus")) {
				val = ref.plus(period);
			} else if (dir.equals("minus")) {
				val = ref.minus(period);
			}
		} else {
			val = ref;
			// use set_xxx
			for (Map.Entry entry : (Set<Map.Entry>) params.entrySet()) {
				Matcher m = Pattern.compile("set_(.*)").matcher((String) entry.getKey());
				if (m.matches()) {
					String field = assignValues((String) entry.getValue(), values);
					String fieldName = m.group(1);

					if (fieldName.equals("month")) {
						int month = Integer.parseInt(field);
						val = getTimeAnnotator().normalizeMonth(val, month);
					} else if (fieldName.equals("day")) {
						int day = Integer.parseInt(field);
						val = val.withField(DateTimeFieldType.dayOfMonth(), day);
					} else {
						throw new InternalError();
					}
				}
			}
		}

		String formattedDate = formatter.print(val);
		FeatureSet attrs = new FeatureSet();
		attrs.put("VAL", formattedDate);

		doc.annotate("TIMEX2", span, attrs);
	}

}
