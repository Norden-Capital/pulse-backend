package com.nordencapital.pulse.me;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nordencapital.pulse.auth.LoginEvents;

/**
 * Login attempts per day, for Settings → Security.
 */
@Component
public class SignInHistory {

	/** Default start: this many months back. */
	static final int DEFAULT_MONTHS = 6;

	private final LoginEvents loginEvents;

	private final Clock clock;

	public SignInHistory(LoginEvents loginEvents, Clock clock) {
		this.loginEvents = loginEvents;
		this.clock = clock;
	}

	/**
	 * Days with at least one attempt of the user, oldest first, cut in {@code zone}.
	 * @param from first day; null = today minus {@value #DEFAULT_MONTHS} months
	 */
	@Transactional(readOnly = true)
	public List<SignInDay> days(UUID userId, ZoneId zone, LocalDate from) {
		LocalDate start = (from != null) ? from : LocalDate.now(this.clock.withZone(zone)).minusMonths(DEFAULT_MONTHS);
		Map<LocalDate, int[]> days = new TreeMap<>();
		for (LoginEvents.Attempt attempt : this.loginEvents.since(userId, start.atStartOfDay(zone).toInstant())) {
			int[] counts = days.computeIfAbsent(attempt.at().atZone(zone).toLocalDate(), (day) -> new int[2]);
			counts[attempt.success() ? 0 : 1]++;
		}
		return days.entrySet()
			.stream()
			.map((entry) -> new SignInDay(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
			.toList();
	}

}
