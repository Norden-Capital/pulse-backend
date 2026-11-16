package com.nordencapital.pulse.auth;

public enum TokenPurpose {

	/** Sent after register (and by resend). Confirms the email. */
	VERIFY,

	/** Sent by "forgot password". The user sets a new password. */
	RESET

}
