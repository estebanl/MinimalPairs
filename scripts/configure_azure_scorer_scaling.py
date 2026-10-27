#!/usr/bin/env python3
"""Set the existing scorer's scale-to-zero cooldown without rebuilding its image."""
import argparse
import json
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cooldown', type=int, default=60)
args = parser.parse_args()
if not 30 <= args.cooldown <= 3600:
    parser.error('cooldown must be between 30 and 3600 seconds')

def azure(*command):
    result = subprocess.run(['az', *command, '--only-show-errors', '-o', 'json'],
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Azure scaling operation failed; command output withheld')
    return json.loads(result.stdout) if result.stdout.strip() else None

resource = ['-g', 'minimalpairs-scorer-rg', '-n', 'minimalpairs-scorer']
before = azure('containerapp', 'show', *resource)
assert before['properties']['template']['scale']['minReplicas'] == 0
assert before['properties']['template']['scale']['maxReplicas'] == 1
desired = {'cooldownPeriod': args.cooldown, 'allowScalingRuleOverride': True,
           'rules': [{'name': 'http-scale-rule', 'http': {'metadata': {'concurrentRequests': '10'}}}]}
if any(before['properties']['template']['scale'].get(k) != v for k, v in desired.items()):
    azure('rest', '--method', 'patch', '--url',
          'https://management.azure.com' + before['id'] + '?api-version=2026-03-02-preview',
          '--body', json.dumps({'location': before['location'],
              'properties': {'template': {'scale': desired}}}))
for attempt in range(90):
    after = azure('containerapp', 'show', *resource)
    p = after['properties']
    if p['provisioningState'] == 'Succeeded' and p['latestRevisionName'] == p['latestReadyRevisionName']:
        break
    time.sleep(5)
else:
    raise RuntimeError('Updated revision did not become ready within the rollout timeout')
assert p['template']['containers'] == before['properties']['template']['containers']
assert p['template']['scale']['cooldownPeriod'] == args.cooldown
assert p['template']['scale']['minReplicas'] == 0
assert p['template']['scale']['maxReplicas'] == 1
revision = azure('containerapp', 'revision', 'show', *resource, '--revision', p['latestReadyRevisionName'])
effective = revision['properties']['template']['scale']
# Azure's revision GET currently omits cooldown/polling values even when the
# app GET retains them. Verify the API setting here and actual shutdown in the
# separate cold-start Speak test rather than interpreting null as a default.
assert effective['rules'] == desired['rules'], 'Effective HTTP scale rule differs'
print(json.dumps({'revision':p['latestReadyRevisionName'], 'scale':p['template']['scale'],
                  'effective_revision_scale':effective}, indent=2))
