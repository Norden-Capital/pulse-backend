package com.nordencapital.pulse.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.nordencapital.pulse.auth.AuthUser;
import com.nordencapital.pulse.common.ApiException;

/**
 * Loads the caller again from the database (the session is not trusted for the status),
 * so a disabled user or a stale session gets nothing.
 */
@Component
public class Actors {

	private final UserRepository users;

	public Actors(UserRepository users) {
		this.users = users;
	}

	/**
	 * @throws ApiException 401 {@code unauthorized} if the caller is gone or not ACTIVE
	 */
	public User active(AuthUser principal) {
		return this.users.findById(principal.id())
			.filter((user) -> user.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Please log in"));
	}

}
