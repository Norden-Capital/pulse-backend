package com.nordencapital.pulse.me;

import java.time.LocalDate;

/**
 * Login attempts on one day (in the asked time zone).
 * @param ok successful logins
 * @param failed failed logins
 */
public record SignInDay(LocalDate date, int ok, int failed) {
}
