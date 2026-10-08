package com.wulfnet.enchanting2.network;

import com.wulfnet.enchanting2.Enchanting2;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The network protocol number, carried in every payload ID as {@code enchanting2:v<N>/<name>}. Bump {@link #VERSION}
 * only when a payload's wire format changes, not on every release (decisions.md 2026-10-06). Pure Java, so the join
 * check is unit-tested without Minecraft.
 */
public final class NetworkProtocol {
	public static final int VERSION = 4;

	private static final Pattern OUR_CHANNEL = Pattern.compile(Pattern.quote(Enchanting2.MOD_ID) + ":v(\\d{1,9})/.+");

	private NetworkProtocol() {
	}

	/** The ID path for a payload, e.g. {@code v2/config_sync}. */
	public static String path(String name) {
		return "v" + VERSION + "/" + name;
	}

	public enum Kind {
		/** The client accepts the server's required channel. */
		MATCH,
		/** The client advertises no Enchanting 2 channel at all. */
		VANILLA,
		/** The client has Enchanting 2, but on another protocol (or a build too old to say which). */
		MISMATCH
	}

	/** @param clientVersions protocol numbers the client advertised; empty when its channels carry none */
	public record Verdict(Kind kind, List<Integer> clientVersions) {
	}

	/**
	 * @param clientChannels  every channel the client said it can receive, as {@code namespace:path}
	 * @param requiredChannel the channel the server must be able to send, e.g. {@code enchanting2:v2/config_sync}
	 */
	public static Verdict classify(Collection<String> clientChannels, String requiredChannel) {
		if (clientChannels.contains(requiredChannel)) {
			return new Verdict(Kind.MATCH, List.of());
		}
		boolean ours = false;
		TreeSet<Integer> versions = new TreeSet<>();
		for (String channel : clientChannels) {
			if (!channel.startsWith(Enchanting2.MOD_ID + ":")) {
				continue;
			}
			ours = true;
			Matcher m = OUR_CHANNEL.matcher(channel);
			if (m.matches()) {
				versions.add(Integer.parseInt(m.group(1)));
			}
		}
		return ours ? new Verdict(Kind.MISMATCH, List.copyOf(versions)) : new Verdict(Kind.VANILLA, List.of());
	}

	/**
	 * The disconnect text. Plain English rather than a translation key: a vanilla client has no lang entry for it.
	 *
	 * @throws IllegalArgumentException for {@link Kind#MATCH}, which must not disconnect
	 */
	public static String disconnectMessage(Verdict verdict, String serverModVersion) {
		return switch (verdict.kind()) {
			case VANILLA -> "This server requires the Enchanting 2 mod (" + Enchanting2.MOD_ID + ") on your client. "
					+ "Install Enchanting 2 " + serverModVersion + " to join.";
			case MISMATCH -> "Enchanting 2 version mismatch. This server runs " + serverModVersion
					+ " (network protocol " + VERSION + "); your client uses protocol "
					+ describe(verdict.clientVersions()) + ". Install Enchanting 2 " + serverModVersion + " to join.";
			case MATCH -> throw new IllegalArgumentException("a matching client is not disconnected");
		};
	}

	private static String describe(List<Integer> versions) {
		if (versions.isEmpty()) {
			return "unknown";
		}
		return versions.stream().map(String::valueOf).collect(Collectors.joining(", "));
	}
}
