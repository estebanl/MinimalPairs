"""Check launch routing with fake Azure responses; never access Azure or a device."""
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import sys
import unittest
from unittest.mock import Mock, patch


SCRIPT = Path(__file__).resolve().parents[1] / 'azure_speak.py'
BASE = 'https://scorer.example'
KEY = 'test-key-not-a-real-credential'


class Launched(Exception):
    pass


class AzureSpeakTest(unittest.TestCase):
    def launch(self, *arguments):
        app = {'properties': {'configuration': {'ingress': {'fqdn': 'scorer.example'}}}}
        keys = {'value': {'keys': [{'name': 'default', 'value': KEY}]}}
        responses = [Mock(returncode=0, stdout=json.dumps(value)) for value in (app, keys)]
        health = Mock(status_code=401)
        authenticated = Mock()
        authenticated.json.return_value = {'status': 'ok'}
        output = io.StringIO()
        with patch.object(sys, 'argv', [str(SCRIPT), *arguments]), \
             patch.dict(os.environ, {}, clear=True), \
             patch('subprocess.run', side_effect=responses), \
             patch('httpx.get', side_effect=[health, authenticated]), \
             patch('os.chdir'), \
             patch('os.execvpe', side_effect=Launched) as execute, \
             contextlib.redirect_stdout(output):
            with self.assertRaises(Launched):
                runpy.run_path(str(SCRIPT), run_name='__main__')
        self.assertNotIn(KEY, output.getvalue())
        executable, command, environment = execute.call_args.args
        self.assertNotIn(KEY, ' '.join(command))
        return executable, command, environment

    def test_ios_passes_credentials_in_device_environment_before_bundle_argument(self):
        executable, command, environment = self.launch(
            'ios', '--device', 'test-phone', '--bundle-id', 'test.bundle', '--console')
        self.assertEqual(executable, 'xcrun')
        self.assertEqual(command, ['xcrun', 'devicectl', 'device', 'process', 'launch',
                                   '--device', 'test-phone', '--terminate-existing', '--console', 'test.bundle'])
        self.assertEqual(environment['DEVICECTL_CHILD_MINIMAL_PAIRS_SCORER_URL'], BASE)
        self.assertEqual(environment['DEVICECTL_CHILD_MINIMAL_PAIRS_SCORER_KEY'], KEY)
        self.assertNotIn('MINIMAL_PAIRS_SCORER_KEY', environment)

    def test_desktop_launch_still_passes_its_own_environment(self):
        _, command, environment = self.launch('run')
        self.assertEqual(command[-1], ':composeApp:run')
        self.assertEqual(environment['MINIMAL_PAIRS_SCORER_URL'], BASE)
        self.assertEqual(environment['MINIMAL_PAIRS_SCORER_KEY'], KEY)
        self.assertNotIn('DEVICECTL_CHILD_MINIMAL_PAIRS_SCORER_KEY', environment)

    def test_verification_mode_still_selects_live_test(self):
        _, command, environment = self.launch('verify')
        self.assertEqual(command[-1], '*LiveSpeakScoringTest')
        self.assertEqual(environment['MINIMAL_PAIRS_LIVE_SCORING'], '1')

    def test_all_verifies_speak_and_stories_without_persisting_credentials(self):
        _, command, environment = self.launch('verify', '--module', 'all')
        self.assertIn('*LiveSpeakScoringTest', command)
        self.assertIn('*LiveStoriesScoringTest', command)
        self.assertEqual(environment['MINIMAL_PAIRS_LIVE_STORIES'], '1')
        self.assertEqual(environment['MINIMAL_PAIRS_LIVE_SCORING'], '1')

    def test_stories_only_does_not_select_speak(self):
        _, command, environment = self.launch('verify', '--module', 'stories')
        self.assertEqual(command[-1], '*LiveStoriesScoringTest')
        self.assertNotIn('*LiveSpeakScoringTest', command)
        self.assertEqual(environment['MINIMAL_PAIRS_LIVE_STORIES'], '1')

    def test_invalid_ios_options_fail_before_accessing_azure(self):
        for arguments in [('ios',), ('ios', '--device', 'phone', '--cold-start'), ('run', '--device', 'phone')]:
            with self.subTest(arguments=arguments), \
                 patch.object(sys, 'argv', [str(SCRIPT), *arguments]), \
                 patch('subprocess.run') as azure, \
                 contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as error:
                    runpy.run_path(str(SCRIPT), run_name='__main__')
                self.assertEqual(error.exception.code, 2)
                azure.assert_not_called()


if __name__ == '__main__':
    unittest.main()
