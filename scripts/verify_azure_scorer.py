#!/usr/bin/env python3
"""Check deployed Functions auth and scoring without printing/persisting keys.

Only bundled example audio and generated noise are sent to Azure, never retained
microphone captures. Requires az login and the backend test dependencies.
"""
import argparse
import io
import json
from pathlib import Path
import subprocess
import wave
import time

import httpx
import numpy as np

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--resource-group', default='minimalpairs-scorer-rg')
parser.add_argument('--app', default='minimalpairs-scorer')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]

def azure(*command):
    attempts = 3 if 'keys' in command else 1
    for attempt in range(attempts):
        result = subprocess.run(['az', *command, '--only-show-errors', '-o', 'json'],
                                capture_output=True, text=True)
        if not result.returncode:
            return json.loads(result.stdout)
        if attempt + 1 < attempts:
            time.sleep(5)
    raise RuntimeError('Azure configuration lookup failed; credential output withheld')

app = azure('containerapp', 'show', '-g', args.resource_group, '-n', args.app)
base = 'https://' + app['properties']['configuration']['ingress']['fqdn']
with httpx.Client(timeout=180) as client:
    unauthorized = client.get(base + '/health')
    assert unauthorized.status_code == 401, f'Expected key protection, got {unauthorized.status_code}'
    keys = azure('containerapp', 'function', 'keys', 'list', '-g', args.resource_group,
                 '-n', args.app, '--key-type', 'hostKey')['value']['keys']
    key = next(k['value'] for k in keys if k['name'] == 'default')
    headers = {'x-functions-key': key}
    health = client.get(base + '/health', headers=headers)
    health.raise_for_status()
    results = []
    for word in ('pear', 'bear'):
        audio = (root / f'composeApp/src/commonMain/composeResources/files/audio/words/{word}.wav').read_bytes()
        response = client.post(base + '/v1/score/phoneme', headers=headers,
            data={'target_transcript': 'pear', 'expected_ipa': 'p eə', 'target_phoneme_index': '0'},
            files={'audio_file': (word + '.wav', audio, 'audio/wav')})
        response.raise_for_status()
        body = response.json()
        assert body['scoring_method'] == 'neural_ctc_phoneme'
        assert body['target_phoneme_index'] == 0
        assert body['overall_score'] == body['phonemes'][0]['gop_score']
        results.append({'input': word, 'response': body})
    assert results[0]['response']['overall_score'] > 70
    assert results[1]['response']['overall_score'] < 10
    buffer = io.BytesIO()
    with wave.open(buffer, 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(np.random.default_rng(42).integers(-1000, 1001, 16000, dtype=np.int16).tobytes())
    noise = client.post(base + '/v1/score/phoneme', headers=headers,
        data={'target_transcript': 'pear', 'expected_ipa': 'p eə', 'target_phoneme_index': '0'},
        files={'audio_file': ('noise.wav', buffer.getvalue(), 'audio/wav')})
    assert noise.status_code == 400
    assert noise.json()['detail']['code'] == 'noisy_audio'
    report = {'url': base, 'unauthenticated_status': unauthorized.status_code,
              'health': client.get(base + '/health', headers=headers).json(),
              'results': results, 'noise': noise.json()}
output = root / 'build/reports/azure/scorer-smoke.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
print(json.dumps(report, indent=2, ensure_ascii=False))
