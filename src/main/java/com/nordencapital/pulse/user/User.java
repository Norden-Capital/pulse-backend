package com.nordencapital.pulse.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	/** Argon2 hash with its {id} prefix. */
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	protected User() {
	}

	/**
	 * A new account from the register form: PENDING_VERIFICATION until the email is
	 * confirmed.
	 * @param email already normalized (trimmed, lowercase)
	 */
	public static User register(String email, String passwordHash, Instant now) {
		User user = new User();
		user.id = UUID.randomUUID();
		user.email = email;
		user.passwordHash = passwordHash;
		user.status = UserStatus.PENDING_VERIFICATION;
		user.createdAt = now;
		user.updatedAt = now;
		return user;
	}

	public UUID getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public UserStatus getStatus() {
		return this.status;
	}

	public Instant getEmailVerifiedAt() {
		return this.emailVerifiedAt;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public void setPasswordHash(String passwordHash, Instant now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

	public void setStatus(UserStatus status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	/**
	 * The user proved they own the inbox (verify link or reset link): ACTIVE, and the
	 * first verification time is kept.
	 */
	public void activate(Instant now) {
		this.status = UserStatus.ACTIVE;
		if (this.emailVerifiedAt == null) {
			this.emailVerifiedAt = now;
		}
		this.updatedAt = now;
	}

	public void recordLogin(Instant now) {
		this.lastLoginAt = now;
	}

}
