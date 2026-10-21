package com.wulfnet.enchanting2.config.form;

import java.util.List;
import java.util.Objects;

/**
 * Everything the config screen shows (DESIGN §1: every option is on it), for both screens: YACL's on Fabric and ours
 * ({@code client/config/FormScreen}) on NeoForge, Forge and Fabric without YACL. Built by {@link ConfigForms}.
 */
public final class ConfigForm {
	private final List<FormCategory> categories;
	private final Runnable save;

	/** @param save writes the files whose working copy changed; the working copies are already set when it runs */
	ConfigForm(List<FormCategory> categories, Runnable save) {
		this.categories = List.copyOf(categories);
		this.save = Objects.requireNonNull(save, "save");
	}

	public FormText title() {
		return FormText.of("enchanting2.config.title");
	}

	public List<FormCategory> categories() {
		return categories;
	}

	public List<FormOption<?>> options() {
		return categories.stream().flatMap(category -> category.options().stream()).toList();
	}

	/** Any option's pending value differs from the file's: Save would write something. */
	public boolean changed() {
		return options().stream().anyMatch(FormOption::changed);
	}

	/** YACL's Undo: every pending value back to the file's. */
	public void undo() {
		options().forEach(FormOption::undo);
	}

	/** YACL's Reset: every editable option to its built-in value, pending until saved (so Undo takes it back). */
	public void resetAll() {
		options().forEach(FormOption::reset);
	}

	/** YACL's save hook: YACL has already set each changed option's working copy. */
	public void save() {
		save.run();
	}

	/** Our screen's Save: moves every pending value into the working copies, then saves. */
	public void applyAndSave() {
		options().forEach(FormOption::apply);
		save();
	}
}
