#!/usr/bin/env python3

# Usage: add-permissions.py

# Adds permissions to the default user diku_admin

import requests
import json

# Get token
headers={'x-okapi-tenant': 'diku', 'content-type': 'application/json'}
payload = {'username': 'diku_admin', 'password': 'admin'}
response = requests.post('http://oriole-test.library.jhu.edu:9130/authn/login', data=json.dumps(payload), headers=headers)
print(response)
token = response.headers['x-okapi-token']

api_url = 'http://oriole-test.library.jhu.edu:9130/oriole-resources'
headers['x-okapi-token'] = token

# Adding permissions
permissions = [
    'oriole.resources.admin',
    'oriole.libraries.admin',
    'oriole.subjects.admin'
]

user_url = 'http://oriole-test.library.jhu.edu:9130/users?query=username=diku_admin'
response = requests.get(user_url, headers=headers)
user_id = response.json()['users'][0]['id']

perms_url = f'http://oriole-test.library.jhu.edu:9130/perms/users/{user_id}/permissions?indexField=userId'
response = requests.get(perms_url, headers=headers)
total = response.json()['totalRecords']
print(f'Number of permissions: {total}')

for permission in permissions:
    response = requests.post(perms_url, headers=headers, data=json.dumps({'permissionName': permission}))
    if response.status_code != 200:
        print(f'Adding permission failed: [{response.status_code}] {response.text}')

response = requests.get(perms_url, headers=headers)
total = response.json()['totalRecords']
print(f'Number of permissions: {total}')
