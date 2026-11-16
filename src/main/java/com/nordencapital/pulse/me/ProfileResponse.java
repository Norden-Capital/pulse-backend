package com.nordencapital.pulse.me;

import java.time.Instant;
import java.util.UUID;

import com.nordencapital.pulse.user.User;
import com.nordencapital.pulse.user.UserStatus;

/**
 * The logged-in user's own profile.
 * @param lastLoginAt this session's login
 * @param previousLoginAt the successful login before it, or null
 */
public record ProfileResponse(UUID id, String email, UserStatus status, Instant createdAt, Instant emailVerifiedAt,
		Instant lastLoginAt, Instant previousLoginAt) {

	static ProfileResponse from(User user, Instant previousLoginAt) {
		return new ProfileResponse(user.getId(), user.getEmail(), user.getStatus(), user.getCreatedAt(),
				user.getEmailVerifiedAt(), user.getLastLoginAt(), previousLoginAt);
	}

}
