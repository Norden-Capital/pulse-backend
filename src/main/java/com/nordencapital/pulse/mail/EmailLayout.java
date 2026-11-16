package com.nordencapital.pulse.mail;

/**
 * The one email layout for Pulse: wordmark, title, one paragraph, one black button,
 * footer. Email-safe HTML (tables + inline styles, no SVG, no images, no external CSS)
 * plus a plain-text version. Reuse it for every new email.
 * <p>
 * There is no brand logo yet, so the top is the text wordmark "Pulse" (it also shows
 * when images are blocked, because it is not an image).
 */
public final class EmailLayout {

	static final String WORDMARK = "Pulse";

	private static final String FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";

	private static final String FOOTER = "If you didn't expect this email, you can safely ignore it.";

	private static final String COMPANY = "Norden Capital";

	private EmailLayout() {
	}

	/**
	 * An email with one action button.
	 * @param preheader short preview text shown in the inbox list
	 * @param title bold title, e.g. "Confirm your email"
	 * @param body one or two sentences under the title
	 * @param buttonText e.g. "Confirm email"
	 * @param link the button link
	 * @param extraText optional line under the button, e.g. "Forgot your password?"; null
	 * for none
	 * @param extraLink link for {@code extraText}; null for none
	 */
	public record ActionEmail(String preheader, String title, String body, String buttonText, String link,
			String extraText, String extraLink) {

		public ActionEmail(String preheader, String title, String body, String buttonText, String link) {
			this(preheader, title, body, buttonText, link, null, null);
		}

		boolean hasExtra() {
			return this.extraText != null && this.extraLink != null;
		}

	}

	public static String html(ActionEmail email) {
		String link = escape(email.link());
		String extra = email.hasExtra() ? """
				<tr><td align="center" style="padding-bottom:32px; font-family:%1$s; font-size:14px; line-height:20px; color:#52525b;">%2$s <a href="%3$s" target="_blank" style="color:#111111; font-weight:600;">%4$s</a></td></tr>
				""".formatted(FONT, escape(email.extraText()), escape(email.extraLink()),
				escape(shortLink(email.extraLink()))) : "";
		return """
				<!doctype html>
				<html lang="en">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<meta name="color-scheme" content="light only">
				<title>%1$s</title>
				</head>
				<body style="margin:0; padding:0; background-color:#ffffff;">
				<div style="display:none; max-height:0; overflow:hidden; opacity:0; mso-hide:all;">%2$s</div>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" bgcolor="#ffffff" style="background-color:#ffffff;">
				<tr><td align="center" style="padding:56px 16px;">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;">
				<tr><td align="center" style="padding-bottom:36px; font-family:%3$s; font-size:22px; line-height:28px; font-weight:800; letter-spacing:-0.5px; color:#111111;">%9$s</td></tr>
				<tr><td align="center" style="padding-bottom:12px; font-family:%3$s; font-size:24px; line-height:32px; font-weight:700; color:#111111;">%1$s</td></tr>
				<tr><td align="center" style="padding-bottom:32px; font-family:%3$s; font-size:16px; line-height:24px; color:#52525b;">%4$s</td></tr>
				<tr><td align="center" style="padding-bottom:40px;">
				<table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
				<td align="center" bgcolor="#111111" style="background-color:#111111; border-radius:8px;">
				<a href="%5$s" target="_blank" style="display:inline-block; padding:12px 28px; font-family:%3$s; font-size:16px; line-height:24px; font-weight:700; color:#ffffff; text-decoration:none; border-radius:8px;">%6$s</a>
				</td>
				</tr></table>
				</td></tr>
				%11$s<tr><td align="center" style="padding-bottom:32px; font-family:%3$s; font-size:12px; line-height:18px; color:#a1a1aa;">Button not working? Use this link:<br><a href="%5$s" target="_blank" style="color:#71717a; white-space:nowrap;">%10$s</a></td></tr>
				<tr><td align="center" style="border-top:1px solid #e4e4e7; padding-top:24px; font-family:%3$s; font-size:12px; line-height:18px; color:#a1a1aa;">%7$s<br>%8$s</td></tr>
				</table>
				</td></tr>
				</table>
				</body>
				</html>
				""".formatted(escape(email.title()), escape(email.preheader()), FONT, escape(email.body()), link,
				escape(email.buttonText()), escape(FOOTER), escape(COMPANY), escape(WORDMARK),
				escape(shortLink(email.link())), extra);
	}

	/**
	 * What a fallback link shows: host + path, without "https://" and without the token
	 * ("pulse.example.com/verify-email"). The href keeps the full URL.
	 */
	static String shortLink(String link) {
		String shown = link.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
		int end = shown.length();
		for (char c : new char[] { '?', '#' }) {
			int i = shown.indexOf(c);
			if (i >= 0 && i < end) {
				end = i;
			}
		}
		return shown.substring(0, end);
	}

	public static String text(ActionEmail email) {
		String extra = email.hasExtra() ? email.extraText() + " " + email.extraLink() + "\n\n" : "";
		return """
				%s

				%s

				%s: %s

				%s%s
				%s
				""".formatted(email.title(), email.body(), email.buttonText(), email.link(), extra, FOOTER, COMPANY);
	}

	static String escape(String value) {
		StringBuilder out = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				case '\'' -> out.append("&#39;");
				default -> out.append(c);
			}
		}
		return out.toString();
	}

}
