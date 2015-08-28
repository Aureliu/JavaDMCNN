// -*- tab-width: 4 -*-
package Jet.Time;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import pnuts.lang.Context;
import pnuts.lang.ParseException;
import pnuts.lang.Pnuts;
import pnuts.lang.PnutsException;
import pnuts.lang.PnutsFunction;
import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class ScriptRule extends TimeRule {
	private Pnuts pnuts;

	@Override
	public void setParameters(Map params) {
		super.setParameters(params);

		try {
			String script = (String) params.get("script");
			pnuts = Pnuts.parse(script);
		} catch (ParseException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public void apply(Document doc, List<Object> values, Span span, DateTime ref) {
		Map params = getParameters();

		Map config = getTimeAnnotator().getConfig();

		String format = (String) params.get("format");
		DateTimeFormatter formatter = null;
		if (format != null) {
			formatter = DateTimeFormat.forPattern(format);
		}

		pnuts.lang.Package pkg = new pnuts.lang.Package();
		pkg.set("ref", ref);
		pkg.set("formatter", formatter);
		pkg.set("values", values);
		pkg.set("config", config);
		pkg.set("doc", doc);
		pkg.set("span", span);
		pkg.set("annotate", new PnutsAnnotateFunction(doc));
		pkg.set("removeAnnotation", new PnutsRemoveAnnotationFunction(doc));
		Context context = new Context(pkg);

		Object obj = null;
		try {
			obj = pnuts.run(context);
		} catch (PnutsException e) {
			if (e.getThrowable() instanceof RuntimeException) {
				throw (RuntimeException) e.getThrowable();
			} else {
				throw e;
			}
		}
		
		if (obj instanceof DateTime) {
			String val = formatter.print((DateTime) obj);

			FeatureSet attrs = new FeatureSet();
			attrs.put("VAL", val);
			doc.annotate("TIMEX2", span, attrs);
		} else if (obj instanceof FeatureSet) {
			FeatureSet attrs = (FeatureSet) obj;
			doc.annotate("TIMEX2", span, attrs);
		} else if (obj != null) {
			throw new RuntimeException();
		}
	}

	private static class PnutsAnnotateFunction extends PnutsFunction {
		private Document doc;

		public PnutsAnnotateFunction(Document doc) {
			this.doc = doc;
		}

		@Override
		public boolean defined(int n) {
			return n == 2;
		}

		@Override
		public Object exec(Object[] args, Context context) {
			if (!defined(args.length)) {
				undefined(args, context);
				return null;
			}

			Span span = (Span) args[0];
			FeatureSet attrs;
			if (args[1] instanceof FeatureSet) {
				attrs = (FeatureSet) args[1];
			} else if (args[1] instanceof Map) {
				Map<String, ?> map = (Map<String, ?>) args[1];
				attrs = new FeatureSet();
				for (Map.Entry<String, ?> entry : map.entrySet()) {
					attrs.put(entry.getKey(), entry.getValue());
				}
			} else {
				undefined(args, context);
				return null;
			}

			doc.annotate("TIMEX2", span, attrs);

			return null;
		}
	}

	private static class PnutsRemoveAnnotationFunction extends PnutsFunction {
		private Document doc;

		public PnutsRemoveAnnotationFunction(Document doc) {
			this.doc = doc;
		}

		@Override
		public boolean defined(int n) {
			return n == 1;
		}

		@Override
		public Object exec(Object[] args, Context context) {
			if (!defined(args.length)) {
				undefined(args, context);
				return null;
			}

			Annotation a = (Annotation) args[0];
			doc.removeAnnotation(a);
			return null;
		}
	}
}
