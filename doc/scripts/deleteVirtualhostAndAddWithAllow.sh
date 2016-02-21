#!/bin/bash

curl -v -XDELETE http://127.0.0.1:9090/virtualhost/lol.localdomain -d "{'id':'lol.localdomain'}"
curl -v -XPOST http://127.0.0.1:9090/virtualhost -d "{'id':'lol.localdomain', 'properties': { 'allow': '10.0.0.1,127.0.0.1,172.20.0.2' }}"

