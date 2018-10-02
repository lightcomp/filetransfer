package com.lightcomp.ft.server;

import java.util.Map;

/**
 * Error description of transfer failure.
 */
public interface ErrorDesc {

	/**
	 * Text description.
	 */
	String getMessage();

	/**
	 * Optional detail.
	 */
	String getDetail();

	/**
	 * Optional parameters for more precise description.
	 */
	Map<String, Object> getParams();

	/**
	 * Optional stack trace.
	 */
	StackTraceElement[] getStackTrace();

	/**
	 * Appends error description to string builder separated by comma.
	 */
	default void appendTo(StringBuilder sb, boolean includeStackTrace) {
		// append description
		if (sb.length() > 0) {
			sb.append(", error=");
		}
		sb.append(getMessage());
		// append detail
		String detail = getDetail();
		if (detail != null) {
			sb.append(", errorDetail=").append(detail);
		}
		// append parameters
		Map<String, Object> params = getParams();
		if (params != null && params.size() > 0) {
			sb.append(", errorParams=[");
			params.forEach((n, v) -> sb.append(n).append('=').append(v).append(','));
			// replace last separator with bracket
			sb.setCharAt(sb.length() - 1, ']');
		}
		// append stack trace
		StackTraceElement[] stackTrace = getStackTrace();
		if (includeStackTrace && stackTrace != null) {
			sb.append(", errorStackTrace:").append(System.lineSeparator());
			for (StackTraceElement ste : stackTrace) {
				sb.append(ste);
			}
		}
	}
}
