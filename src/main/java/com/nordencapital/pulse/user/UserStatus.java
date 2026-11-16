package com.nordencapital.pulse.user;

public enum UserStatus {

	/** Registered, has a password, has not confirmed the email yet. Cannot log in. */
	PENDING_VERIFICATION,

	/** Email confirmed. Can log in. */
	ACTIVE,

	/** Blocked (set by hand in the database for now). Cannot log in. */
	DISABLED

}
