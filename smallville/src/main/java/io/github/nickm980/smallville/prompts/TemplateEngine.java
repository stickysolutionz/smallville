package io.github.nickm980.smallville.prompts;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

public class TemplateEngine {

    /**
     * Mustache HTML-escapes substituted values by default, which is never what
     * a language model prompt wants: an agent trait containing an apostrophe
     * arrived as "Klaus&#39;s room", and a newline as "&#10;". The previous
     * code unescaped newlines afterwards with a string replace, which fixed
     * the symptom it had noticed and left quotes, apostrophes and ampersands
     * corrupted - exactly the characters that fill diary text and dialogue.
     */
    private static class PlainTextMustacheFactory extends DefaultMustacheFactory {
	@Override
	public void encode(String value, java.io.Writer writer) {
	    try {
		writer.write(value);
	    } catch (java.io.IOException e) {
		throw new com.github.mustachejava.MustacheException(e);
	    }
	}
    }

    public String format(String template, Map<String, Object> data) {
	MustacheFactory mf = new PlainTextMustacheFactory();

	Mustache mustache = mf.compile(new StringReader(template), template);
	StringWriter writer = new StringWriter();
	mustache.execute(writer, data);
	return writer.toString().trim();
    }
}
