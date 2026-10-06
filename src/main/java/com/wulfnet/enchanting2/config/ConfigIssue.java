package com.wulfnet.enchanting2.config;

/**
 * One problem found while reading a config file. The value falls back to its default and the file is left as it is.
 *
 * @param key          dotted path, e.g. {@code experience.flatten_after_level}
 * @param badValue     the value as written, or {@code (missing)}
 * @param defaultValue the value used instead, or {@code null} when the entry is ignored
 * @param reason       why the value was rejected
 */
public record ConfigIssue(String key, String badValue, String defaultValue, String reason) {
	public String describe() {
		return key + " = " + badValue + ": " + reason
				+ (defaultValue == null ? "; ignored" : "; using default " + defaultValue);
	}
}
