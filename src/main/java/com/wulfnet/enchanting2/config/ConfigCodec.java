package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;

/**
 * How one config file maps to its record. {@link ConfigFiles} owns {@code schema_version}, unknown-key reporting and
 * all disk access; a codec only reads and writes its own keys.
 */
public interface ConfigCodec<T> {
	/** The file name inside {@code config/enchanting2/}. Load and save refuse any other name. */
	String fileName();

	/** The newest {@code schema_version} this build understands. */
	int schemaVersion();

	T defaults();

	/** Reads every key, recording bad values on the reader. Never throws on bad input. */
	T read(ConfigReader root);

	/** Appends this file's keys, with comments, after {@code schema_version}. */
	void write(T value, JsonObject root);
}
