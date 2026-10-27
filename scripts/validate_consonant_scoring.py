"""Exercise selected-consonant scoring on bundled word examples using a local model.

Integration diagnostics only, not an independent pronunciation accuracy benchmark.
"""
import argparse
import json, os, re, sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--model", type=Path, default=root / "build/models/phoneme-ctc")
args = parser.parse_args()
sys.path.insert(0, str(root / 'backend'))
os.environ['PHONEME_MODEL_DIR'] = str(args.model.resolve())
os.environ.setdefault('PHONEME_MODEL_ID', args.model.name)
from fastapi.testclient import TestClient
from app.main import app
catalog = (root / 'composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/ListeningContrastsCatalog.kt').read_text()
transcripts = dict(re.findall(r'"([^"\n]+)" to "([^"\n]+)"', (root / 'composeApp/src/commonMain/kotlin/com/minimalpairs/app/data/catalog/ConsonantScoringTargets.kt').read_text()))
client = TestClient(app)
results = []
for block in catalog.split('ListeningContrast(')[2:]:
    header = re.search(r'"([^"]+)", "([^"]+)", "([^"]+)"', block)
    if not header: continue
    contrast, p1, p2 = header.groups()
    for w1, w2 in re.findall(r'"([^"]+)" to "([^"]+)"', block.split('),')[0]):
        for word, phoneme in ((w1, p1), (w2, p2)):
            ipa = transcripts[word]
            index = ipa.split().index(phoneme)
            response = client.post('/v1/score/phoneme', data={'target_transcript': word, 'expected_ipa': ipa, 'target_phoneme_index': index}, files={'audio_file': (word+'.wav', (root / f'composeApp/src/commonMain/composeResources/files/audio/words/{word}.wav').read_bytes(), 'audio/wav')})
            result = {'contrast': contrast, 'word': word, 'phoneme': phoneme, 'status': response.status_code, 'response': response.json()}
            other = w2 if word == w1 else w1
            opposite = client.post('/v1/score/phoneme', data={'target_transcript': word, 'expected_ipa': ipa, 'target_phoneme_index': index}, files={'audio_file': (other+'.wav', (root / f'composeApp/src/commonMain/composeResources/files/audio/words/{other}.wav').read_bytes(), 'audio/wav')})
            result['opposite_status'] = opposite.status_code
            result['opposite_response'] = opposite.json()
            if response.status_code == 200:
                body = response.json()
                assert body['target_phoneme_index'] == index
                assert body['overall_score'] == body['phonemes'][index]['gop_score']
                assert body['phonemes'][index]['phoneme'] == phoneme
            results.append(result)
            print(word, phoneme, response.status_code, round(response.json().get('overall_score', -1), 1), flush=True)
output = root / 'build/reports/phoneme-model/consonant-selected.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(results, ensure_ascii=False, indent=2))
print('Requests:', len(results), 'accepted:', sum(r['status']==200 for r in results))

assert len(results) == 68, 'Catalog parsing missed consonant examples'
