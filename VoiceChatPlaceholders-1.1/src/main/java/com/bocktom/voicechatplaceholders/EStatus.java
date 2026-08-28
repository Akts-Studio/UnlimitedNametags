package com.bocktom.voicechatplaceholders;

public enum EStatus {
	TALKING("talking"),
	WHISPERING("whispering"),
	QUIET("quiet"),
	DISABLED("disabled"),
	NOT_INSTALLED("not_installed");

	public final String key;

	EStatus(String key) {
		this.key = key;
	}
}
