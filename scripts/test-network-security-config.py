#!/usr/bin/env python3
"""Check SNAP's exact HTTP exceptions and the isolated legacy BoxPanel overlay.

This source-policy check needs only Python's standard library. It does not
replace checking the merged resources of the signed APK before publication.
"""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
SNAP_HTTP_HOSTS = {'ccf.center', 'tv.newplytv.online'}


def box_blocks(source):
    """Return the ranges of explicit if(box) blocks in the small build file."""
    blocks = []
    for match in re.finditer(r'\bif\s*\(\s*box\s*\)\s*\{', source):
        depth = 1
        cursor = match.end()
        while cursor < len(source) and depth:
            depth += (source[cursor] == '{') - (source[cursor] == '}')
            cursor += 1
        if depth:
            raise AssertionError('Unclosed if(box) block in app/build.gradle')
        blocks.append((match.start(), cursor))
    return blocks


class NetworkSecurityConfigTests(unittest.TestCase):
    def setUp(self):
        self.main = ET.parse(ROOT / 'app/src/main/res/xml/network_security_config.xml').getroot()
        self.overlay = ET.parse(ROOT / 'app/src/boxpanel/res/xml/network_security_config.xml').getroot()
        self.gradle = (ROOT / 'app/build.gradle').read_text()
        self.manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()

    def test_snap_denies_cleartext_by_default(self):
        self.assertEqual(self.main.tag, 'network-security-config')
        self.assertEqual(len(self.main.findall('base-config')), 1)
        self.assertEqual(self.main.find('base-config').get('cleartextTrafficPermitted'), 'false')
        self.assertIsNone(self.main.find('debug-overrides'))

    def test_snap_only_allows_the_two_exact_provider_hosts(self):
        domains = self.main.findall('./domain-config/domain')
        self.assertEqual(len(domains), len(SNAP_HTTP_HOSTS))
        self.assertEqual({domain.text for domain in domains}, SNAP_HTTP_HOSTS)
        for config in self.main.findall('domain-config'):
            self.assertEqual(config.get('cleartextTrafficPermitted'), 'true')
            self.assertGreater(len(config.findall('domain')), 0)
            self.assertIsNone(config.find('domain-config'), 'Nested policies could broaden the exception')
            for domain in config.findall('domain'):
                self.assertEqual(domain.get('includeSubdomains'), 'false')
                self.assertIn(domain.text, SNAP_HTTP_HOSTS)
        for config in self.main:
            if config.tag != 'domain-config':
                self.assertNotEqual(config.get('cleartextTrafficPermitted'), 'true')

    def test_snap_has_no_wildcard_or_apex_exception(self):
        hosts = {node.text for node in self.main.findall('.//domain')}
        self.assertTrue(all(host and not host.startswith('.') and '*' not in host for host in hosts))
        for unrelated in ('api.snaptvnow.com', 'snaptvnow.com', 'example.com',
                          'other.ccf.center', 'fakeccf.center',
                          'other.tv.newplytv.online', 'newplytv.online'):
            self.assertNotIn(unrelated, hosts)

    def test_manifest_uses_the_selected_policy_and_explicit_variant_default(self):
        application = self.manifest.find('application')
        self.assertEqual(application.get(ANDROID + 'networkSecurityConfig'), '@xml/network_security_config')
        self.assertEqual(application.get(ANDROID + 'usesCleartextTraffic'), '${cleartextTraffic}')
        self.assertRegex(self.gradle, r"cleartextTraffic\s*:\s*box\s*\?\s*'true'\s*:\s*'false'")

    def test_boxpanel_legacy_policy_is_separate_and_unchanged_in_scope(self):
        self.assertEqual(self.overlay.tag, 'network-security-config')
        self.assertEqual(len(self.overlay), 1)
        self.assertEqual(self.overlay.find('base-config').get('cleartextTrafficPermitted'), 'true')
        self.assertEqual(self.overlay.findall('.//domain'), [])

    def test_boxpanel_overlay_is_only_wired_inside_the_box_build(self):
        ranges = box_blocks(self.gradle)
        self.assertTrue(ranges, 'An explicit BoxPanel resource branch is required')
        overlay_pattern = r"sourceSets\.(?:release|debug)\.res\.srcDir\s+file\(\s*['\"]src/boxpanel/res['\"]\s*\)"
        matching = [(start, end) for start, end in ranges
                    if re.search(overlay_pattern, self.gradle[start:end])]
        self.assertEqual(len(matching), 1, 'The overlay must have exactly one conditional inclusion')
        box_source = self.gradle[matching[0][0]:matching[0][1]]
        for variant in ('release', 'debug'):
            variant_pattern = overlay_pattern.replace('(?:release|debug)', variant)
            self.assertEqual(len(re.findall(variant_pattern, box_source)), 1,
                             'Each BoxPanel variant needs exactly one legacy overlay')
        outside = self.gradle
        for start, end in reversed(ranges):
            outside = outside[:start] + ' ' * (end - start) + outside[end:]
        self.assertNotIn('src/boxpanel/res', outside, 'SNAP builds must never include the BoxPanel overlay')
        self.assertNotRegex(outside, r"sourceSets\.main\.res[^\n]*boxpanel")


if __name__ == '__main__':
    unittest.main(verbosity=2)
