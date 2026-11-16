package com.nordencapital.pulse.common;

/**
 * Short display names (e.g. passkey names): trimmed, no control characters.
 */
public final class Names {

	public static final int MAX_LENGTH = 40;

	/** For {@code @Pattern}: no control characters (new lines, tabs...). */
	public static final String NO_CONTROL_CHARS = "[^\\p{Cntrl}]*";

	private Names() {
	}

	/** Trims the name; null stays null. */
	public static String clean(String name) {
		return (name != null) ? name.strip() : null;
	}

}
