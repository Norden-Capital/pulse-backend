package com.nordencapital.pulse.profile;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Where a value comes from (CLAUDE.md, data provenance): typed by the user, estimated by
 * a formula, or measured by a connected device. Stored and sent in lowercase.
 */
public enum Provenance {

	MANUAL, CALCULATED, DEVICE;

	@JsonValue
	public String value() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Stores the lowercase value ("manual", "calculated", "device"). */
	@Converter
	public static class DbConverter implements AttributeConverter<Provenance, String> {

		@Override
		public String convertToDatabaseColumn(Provenance provenance) {
			return (provenance != null) ? provenance.value() : null;
		}

		@Override
		public Provenance convertToEntityAttribute(String value) {
			return (value != null) ? Provenance.valueOf(value.toUpperCase(Locale.ROOT)) : null;
		}

	}

}
