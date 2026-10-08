package com.wulfnet.enchanting2.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.network.NetworkProtocol.Kind;
import com.wulfnet.enchanting2.network.NetworkProtocol.Verdict;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NetworkProtocolTest {
	private static final String REQUIRED = "enchanting2:" + NetworkProtocol.path("config_sync");

	@Test
	void pathCarriesTheVersion() {
		assertEquals("v" + NetworkProtocol.VERSION + "/config_sync", NetworkProtocol.path("config_sync"));
	}

	/**
	 * v4 (Stage 7B): the enchanting table panel, selection and enchant. A v3 client waits for vanilla table offers the
	 * server never makes, so it must not join; nor older ones (v3 the grindstone, v2 and v1 the anvil).
	 */
	@Test
	void protocolIsV4AndOlderClientsAreAMismatch() {
		assertEquals(4, NetworkProtocol.VERSION);
		for (int old : new int[] {1, 2, 3}) {
			Verdict v = NetworkProtocol.classify(Set.of("minecraft:brand", "enchanting2:v" + old + "/config_sync"),
					REQUIRED);
			assertEquals(new Verdict(Kind.MISMATCH, List.of(old)), v);
			assertTrue(NetworkProtocol.disconnectMessage(v, "0.1.0").contains("uses protocol " + old));
		}
	}

	@Test
	void clientWithTheRequiredChannelMatches() {
		Verdict v = NetworkProtocol.classify(Set.of("minecraft:brand", REQUIRED), REQUIRED);
		assertEquals(Kind.MATCH, v.kind());
	}

	@Test
	void clientWithNoEnchantingChannelIsVanilla() {
		Verdict v = NetworkProtocol.classify(Set.of("minecraft:brand", "fabric:registry/sync"), REQUIRED);
		assertEquals(new Verdict(Kind.VANILLA, List.of()), v);
	}

	@Test
	void emptyChannelListIsVanilla() {
		assertEquals(Kind.VANILLA, NetworkProtocol.classify(Set.of(), REQUIRED).kind());
	}

	@Test
	void otherProtocolIsAMismatchAndNamesIt() {
		Verdict v = NetworkProtocol.classify(Set.of("enchanting2:v7/config_sync", "enchanting2:v7/table_pick"),
				REQUIRED);
		assertEquals(new Verdict(Kind.MISMATCH, List.of(7)), v);
		String msg = NetworkProtocol.disconnectMessage(v, "0.1.0");
		assertTrue(msg.contains("protocol " + NetworkProtocol.VERSION), msg);
		assertTrue(msg.contains("uses protocol 7"), msg);
		assertTrue(msg.contains("0.1.0"), msg);
	}

	@Test
	void unversionedEnchantingChannelIsAMismatchWithUnknownProtocol() {
		Verdict v = NetworkProtocol.classify(Set.of("enchanting2:config_sync"), REQUIRED);
		assertEquals(new Verdict(Kind.MISMATCH, List.of()), v);
		assertTrue(NetworkProtocol.disconnectMessage(v, "0.1.0").contains("uses protocol unknown"));
	}

	@Test
	void lookalikeNamespaceIsNotOurs() {
		Verdict v = NetworkProtocol.classify(Set.of("enchanting2x:v1/config_sync", "notenchanting2:v1/a"), REQUIRED);
		assertEquals(Kind.VANILLA, v.kind());
	}

	@Test
	void hugeVersionNumberDoesNotThrow() {
		Verdict v = NetworkProtocol.classify(Set.of("enchanting2:v99999999999/config_sync"), REQUIRED);
		assertEquals(new Verdict(Kind.MISMATCH, List.of()), v);
	}

	@Test
	void vanillaMessageNamesTheModAndVersion() {
		String msg = NetworkProtocol.disconnectMessage(new Verdict(Kind.VANILLA, List.of()), "0.1.0");
		assertTrue(msg.contains("Enchanting 2") && msg.contains("enchanting2") && msg.contains("0.1.0"), msg);
	}

	@Test
	void matchHasNoDisconnectMessage() {
		assertThrows(IllegalArgumentException.class,
				() -> NetworkProtocol.disconnectMessage(new Verdict(Kind.MATCH, List.of()), "0.1.0"));
	}
}
