#!/bin/bash

#curl -v -XDELETE http://127.0.0.1:9090/backendpool/pool1
#
#curl -v -XDELETE http://127.0.0.1:9090/backend/http%3A%2F%2Flocalhost%3A8081
#curl -v -XDELETE http://127.0.0.1:9090/backend/http%3A%2F%2Flocalhost%3A8082
#curl -v -XDELETE http://127.0.0.1:9090/backend/http%3A%2F%2Flocalhost%3A8083
#curl -v -XDELETE http://127.0.0.1:9090/backend/http%3A%2F%2Flocalhost%3A8084
#
#curl -v -XDELETE http://127.0.0.1:9090/virtualhost/lol.localdomain
#
#curl -v -XDELETE http://127.0.0.1:9090/rule/rule1

curl -v -XPOST http://127.0.0.1:9090/backendpool -d "{'id':'pool1'}"
#curl -v -XPOST http://127.0.0.1:9090/backendpool -d "{'id':'pool1', 'properties':{'loadBalancePolicy': 'RandomPolicy'}}"
#curl -v -XPOST http://127.0.0.1:9090/backendpool -d "{'id':'pool1', 'properties':{'loadBalancePolicy': 'IPHashPolicy'}}"

curl -v -XPOST http://127.0.0.1:9090/backend -d "{'id':'http://localhost:8081','parentId':'pool1'}"
curl -v -XPOST http://127.0.0.1:9090/backend -d "{'id':'http://localhost:8082','parentId':'pool1'}"
curl -v -XPOST http://127.0.0.1:9090/backend -d "{'id':'http://localhost:8083','parentId':'pool1'}"
curl -v -XPOST http://127.0.0.1:9090/backend -d "{'id':'http://localhost:8084','parentId':'pool1'}"

curl -v -XPOST http://127.0.0.1:9090/virtualhost -d "{'id':'lol.localdomain'}"

curl -v -XPOST http://127.0.0.1:9090/rule -d "{'id':'rule1', 'parentId':'lol.localdomain', 'properties':{'ruleType':'UriPath','match':'/','orderNum':1,'default':false,'targetType':'BackendPool','targetId':'pool1'}}"

