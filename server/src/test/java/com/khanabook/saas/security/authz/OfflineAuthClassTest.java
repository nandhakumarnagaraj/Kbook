package com.khanabook.saas.security.authz;

import com.khanabook.saas.entity.PermissionKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineAuthClassTest {

    @Test
    void everyPermissionKeyIsClassified() {
        // Guards against adding a PermissionKey (e.g. menu.edit_full) without giving it
        // an explicit offline posture. Unclassified keys default to NEVER_OFFLINE and
        // would be silently quarantined — this test forces an explicit decision.
        assertTrue(OfflineAuthClass.allKeysClassified(),
                "Every PermissionKey must have an explicit OfflineAuthClass mapping");
    }

    @Test
    void menuEditFullIsRevalidatedOnSync() {
        assertNotEquals(OfflineAuthClass.NEVER_OFFLINE,
                OfflineAuthClass.forKey(PermissionKey.MENU_EDIT_FULL.getKey()));
        assertNotEquals(OfflineAuthClass.forKey("this.key.does.not.exist"),
                OfflineAuthClass.forKey(PermissionKey.MENU_EDIT_FULL.getKey()));
    }
}
