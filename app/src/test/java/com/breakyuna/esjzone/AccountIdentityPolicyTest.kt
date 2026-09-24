package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.chooseSharedAccountIdentity
import com.breakyuna.esjzone.network.mirrorLoginOrder
import com.breakyuna.esjzone.network.matchesKnownAccountEmail
import com.breakyuna.esjzone.network.normalizedEmailDigest
import com.breakyuna.esjzone.network.EsjzoneUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountIdentityPolicyTest {
    @Test fun repairLoginAcceptsOnlyTheCurrentEmail() {
        val digest = normalizedEmailDigest("reader@example.test")
        assertEquals(true, matchesKnownAccountEmail(digest, " Reader@Example.Test "))
        assertEquals(false, matchesKnownAccountEmail(digest, "other@example.test"))
        assertNull(matchesKnownAccountEmail(null, "reader@example.test"))
    }

    @Test fun cacheSharesMirrorHostButPreservesPageQuery() {
        assertEquals(
            EsjzoneUrls.canonicalCacheUrl("https://www.esjzone.cc/my/favorite?sort=new"),
            EsjzoneUrls.canonicalCacheUrl("https://www.esjzone.one/my/favorite?sort=new")
        )
        org.junit.Assert.assertNotEquals(
            EsjzoneUrls.canonicalCacheUrl("https://www.esjzone.cc/my/favorite?sort=new"),
            EsjzoneUrls.canonicalCacheUrl("https://www.esjzone.one/my/favorite?sort=udate")
        )
    }

    @Test fun oneInputSchedulesBothMirrorsInSelectedOrder() {
        assertEquals(listOf("mirror-b", "mirror-a"), mirrorLoginOrder(
            "mirror-b", listOf("mirror-a", "mirror-b")
        ))
    }

    @Test fun sameAccountOnAnotherMirrorKeepsOneIdentity() {
        assertEquals("shared-id", chooseSharedAccountIdentity(
            activeEmailDigest = "account-a",
            requestedEmailDigest = "account-a",
            activeIdentity = "shared-id",
            mappedIdentity = "old-mirror-id"
        ))
    }

    @Test fun anotherAccountCannotInheritTheActiveIdentity() {
        assertNull(chooseSharedAccountIdentity(
            activeEmailDigest = "account-a",
            requestedEmailDigest = "account-b",
            activeIdentity = "shared-id",
            mappedIdentity = null
        ))
    }

    @Test fun knownAccountCanRecoverItsPreviousIdentity() {
        assertEquals("saved-id", chooseSharedAccountIdentity(
            activeEmailDigest = "account-a",
            requestedEmailDigest = "account-b",
            activeIdentity = "shared-id",
            mappedIdentity = "saved-id"
        ))
    }
}
