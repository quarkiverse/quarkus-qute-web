package io.quarkiverse.qute.web.markdown.runtime;

/**
 * Represents a converter that receives a markdown as {@code String} and returns
 * an HTML as {@code String}.
 */
public interface MdConverter {

    String html(String text);
}
