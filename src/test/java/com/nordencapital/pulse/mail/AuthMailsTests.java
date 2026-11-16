package com.nordencapital.pulse.mail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import com.nordencapital.pulse.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AuthMailsTests {

	private static final Executor SAME_THREAD = Runnable::run;

	private final List<MailMessage> sent = new ArrayList<>();

	private final AuthMails mails = new AuthMails(this.sent::add, SAME_THREAD,
			properties("https://pulse.example.com/"));

	static AppProperties properties(String frontendUrl) {
		return new AppProperties("", false, frontendUrl,
				new AppProperties.Mail("Pulse <no-reply@example.com>", "", false, false),
				new AppProperties.Auth(Duration.ofHours(24), Duration.ofHours(1), 5, Duration.ofMinutes(15), 3,
						Duration.ofMinutes(15), 10, Duration.ofHours(1), 3, Duration.ofMinutes(15), 5,
						Duration.ofHours(1)));
	}

	@Test
	void verifyMailHasWordmarkTitleButtonAndLinkInHtmlAndText() {
		this.mails.sendVerify("new@example.com", "TOKEN123");
		assertThat(this.sent).hasSize(1);
		MailMessage message = this.sent.get(0);
		assertThat(message.to()).isEqualTo("new@example.com");
		assertThat(message.subject()).isEqualTo("Confirm your email for Pulse");
		assertThat(message.html()).contains(">Pulse</td>")
			.contains("Confirm your email")
			.contains("This link expires in 1 day.")
			.contains("href=\"https://pulse.example.com/verify-email?token=TOKEN123\"")
			.contains(">Confirm email</a>")
			.contains("Button not working? Use this link:")
			.contains(">pulse.example.com/verify-email</a>")
			.contains("you can safely ignore it.")
			.contains("Norden Capital")
			.doesNotContain("<img")
			.doesNotContain("<svg");
		assertThat(message.text()).contains("Confirm your email")
			.contains("This link expires in 1 day.")
			.contains("Confirm email: https://pulse.example.com/verify-email?token=TOKEN123")
			.contains("If you didn't expect this email, you can safely ignore it.")
			.doesNotContain("<");
	}

	@Test
	void resetMailSaysOneHour() {
		this.mails.sendPasswordReset("user@example.com", "TOKEN456");
		MailMessage message = this.sent.get(0);
		assertThat(message.subject()).isEqualTo("Reset your Pulse password");
		assertThat(message.html()).contains("href=\"https://pulse.example.com/set-password?token=TOKEN456\"")
			.contains("This link expires in 1 hour.")
			.contains(">Reset password</a>");
		assertThat(message.text()).contains("Reset password: https://pulse.example.com/set-password?token=TOKEN456");
	}

	@Test
	void accountExistsMailHasNoTokenAndLinksToLoginAndForgotPassword() {
		this.mails.sendAccountExists("ana@example.com");
		MailMessage message = this.sent.get(0);
		assertThat(message.subject()).isEqualTo("You already have a Pulse account");
		assertThat(message.html()).contains("href=\"https://pulse.example.com/login\"")
			.contains(">Sign in</a>")
			.contains("Forgot your password?")
			.contains("href=\"https://pulse.example.com/forgot-password\"")
			.doesNotContain("token")
			.doesNotContain("expires");
		assertThat(message.text()).contains("Sign in: https://pulse.example.com/login")
			.contains("Forgot your password? https://pulse.example.com/forgot-password");
	}

	@Test
	void layoutEscapesHtml() {
		String html = EmailLayout
			.html(new EmailLayout.ActionEmail("p", "<b>x</b>", "a", "B", "https://e.com/?a=1&b=\"2\""));
		assertThat(html).doesNotContain("<b>x</b>")
			.contains("&lt;b&gt;x&lt;/b&gt;")
			.contains("https://e.com/?a=1&amp;b=&quot;2&quot;");
	}

	@Test
	void shortLinkShowsHostAndPathWithoutToken() {
		assertThat(EmailLayout.shortLink("https://pulse-frontend-five-red.vercel.app/verify-email?token=abc"))
			.isEqualTo("pulse-frontend-five-red.vercel.app/verify-email");
		assertThat(EmailLayout.shortLink("http://localhost:3000/set-password#x")).isEqualTo("localhost:3000/set-password");
	}

	@Test
	void timeLeftUsesDaysForWholeDays() {
		assertThat(AuthMails.timeLeft(Duration.ofHours(72))).isEqualTo("3 days");
		assertThat(AuthMails.timeLeft(Duration.ofHours(24))).isEqualTo("1 day");
		assertThat(AuthMails.timeLeft(Duration.ofHours(1))).isEqualTo("1 hour");
		assertThat(AuthMails.timeLeft(Duration.ofHours(12))).isEqualTo("12 hours");
	}

	@Test
	void mailsGoThroughTheExecutor() {
		List<Runnable> queued = new ArrayList<>();
		AuthMails queuedMails = new AuthMails(this.sent::add, queued::add, properties("https://pulse.example.com"));
		queuedMails.sendVerify("new@example.com", "T");
		assertThat(this.sent).isEmpty();
		assertThat(queued).hasSize(1);
		queued.get(0).run();
		assertThat(this.sent).hasSize(1);
	}

	@Test
	void failedSendDoesNotThrow() {
		AuthMails failing = new AuthMails((message) -> {
			throw new IllegalStateException("down");
		}, SAME_THREAD, properties("https://pulse.example.com"));
		assertThatNoException().isThrownBy(() -> failing.sendPasswordReset("x@example.com", "T"));
	}

	@Test
	void fullQueueDropsTheMailWithoutThrowing() {
		AuthMails full = new AuthMails(this.sent::add, (task) -> {
			throw new java.util.concurrent.RejectedExecutionException("full");
		}, properties("https://pulse.example.com"));
		assertThatNoException().isThrownBy(() -> full.sendVerify("x@example.com", "T"));
		assertThat(this.sent).isEmpty();
	}

}
