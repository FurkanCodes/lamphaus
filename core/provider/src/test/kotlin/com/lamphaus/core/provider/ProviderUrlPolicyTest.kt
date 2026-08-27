package com.lamphaus.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderUrlPolicyTest {
    @Test
    fun `production accepts HTTPS and canonicalizes manifest path`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://media.example.org/provider/manifest.json",
            policy.normalizeManifestUrl(" https://media.example.org/provider/ "),
        )
        assertEquals(
            "https://media.example.org/provider/manifest.json?language=en",
            policy.normalizeManifestUrl("https://media.example.org/provider/manifest.json?language=en#ignored"),
        )
    }

    @Test
    fun `production rejects cleartext credentials and malformed addresses`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertNull(policy.normalizeManifestUrl("http://media.example.org"))
        assertNull(policy.normalizeManifestUrl("https://user:secret@media.example.org"))
        assertNull(policy.normalizeManifestUrl("not an address"))
    }

    @Test
    fun `debug permits only explicit emulator loopback hosts over cleartext`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = true)

        assertEquals(
            "http://10.0.2.2:8080/manifest.json",
            policy.normalizeManifestUrl("http://10.0.2.2:8080"),
        )
        assertNull(policy.normalizeManifestUrl("http://192.168.1.5:8080"))
    }

    @Test
    fun `neutral add-on links normalize to HTTPS without retaining custom scheme`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://provider.example/configured/manifest.json?token=abc",
            policy.normalizeManifestUrl("lamphaus://provider.example/configured/manifest.json?token=abc"),
        )
    }

    @Test
    fun `compatible custom install schemes normalize generically`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://provider.example/configured/manifest.json?token=abc",
            policy.normalizeManifestUrl("compatible://provider.example/configured/manifest.json?token=abc"),
        )
    }

    @Test
    fun `underscore hostnames are accepted`() {
        // java.net.URI refuses underscores in hosts; real-world add-ons are
        // routinely hosted at them (e.g. *.workers.dev deployments).
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://my_addon.example.workers.dev/manifest.json",
            policy.normalizeManifestUrl("https://my_addon.example.workers.dev/manifest.json"),
        )
    }

    @Test
    fun `bare domains gain HTTPS and the manifest path`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://v3-cinemeta.strem.io/manifest.json",
            policy.normalizeManifestUrl("v3-cinemeta.strem.io"),
        )
    }

    @Test
    fun `paste artifacts never reach validation`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)
        val nbsp = '\u00A0'
        val zeroWidth = '\u200B'

        assertEquals(
            "https://media.example.org/manifest.json",
            policy.normalizeManifestUrl("${nbsp}https://media.example.org/manifest.json$zeroWidth."),
        )
        assertEquals(
            "https://media.example.org/manifest.json",
            policy.normalizeManifestUrl("\"https://media.example.org/manifest.json\""),
        )
    }

    @Test
    fun `explicit non-manifest json paths are trusted as-is`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://provider.example/addons/v2-provider.json",
            policy.normalizeManifestUrl("https://provider.example/addons/v2-provider.json"),
        )
    }

    @Test
    fun `uppercase schemes and ports canonicalize`() {
        val policy = ProviderUrlPolicy(allowDebugLocalhost = false)

        assertEquals(
            "https://media.example.org:8443/manifest.json",
            policy.normalizeManifestUrl("HTTPS://media.example.org:8443/manifest.json"),
        )
        assertEquals(
            "https://media.example.org/manifest.json",
            policy.normalizeManifestUrl("HTTPS://MEDIA.EXAMPLE.ORG:443/manifest.json"),
        )
    }
}
