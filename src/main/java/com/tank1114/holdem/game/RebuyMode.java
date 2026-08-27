package com.tank1114.holdem.game;

/** Controls what happens when a seated player's stack hits zero. */
public enum RebuyMode {
    /** Automatically topped back up to the configured starting stack as soon as it hits zero. */
    AUTO,
    /** No re-buy at all; a busted player is stood up and must leave the seat. */
    DISABLED,
    /** Busted players are stood up; an admin must use /holdemadmin chips give to let them back in. */
    ADMIN_ONLY
}
