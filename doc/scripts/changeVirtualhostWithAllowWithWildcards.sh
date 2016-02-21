#!/bin/bash

curl -v -XPUT http://127.0.0.1:9090/virtualhost/lol.localdomain -d "{'id':'lol.localdomain', 'properties': { 'allow': '10.0.0.1,127.*.*.*,172.20.0.2' }}"

